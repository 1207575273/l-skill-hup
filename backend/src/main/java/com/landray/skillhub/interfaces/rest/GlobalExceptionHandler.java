package com.landray.skillhub.interfaces.rest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.landray.skillhub.shared.exception.BusinessException;
import com.landray.skillhub.shared.logging.TraceIdConstants;

/**
 * 统一异常映射:业务异常 -> 各自的 HTTP 状态码,参数校验失败 -> 400,未预期异常 -> 500。
 *
 * <p>响应体固定 {@code code / message / traceId / details},{@code traceId} 必填、从 MDC 取(业务建模文档 6.1、
 * backend/CLAUDE.md 第五节)。日志级别按 backend/CLAUDE.md 6.2:参数校验失败与业务异常映射成的 4xx 都是 WARN,只有
 * 未预期异常 / 5xx 才是 ERROR 且必须带栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final int HTTP_STATUS_BAD_REQUEST = 400;
    private static final int HTTP_STATUS_PAYLOAD_TOO_LARGE = 413;
    private static final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;

    private static final String VALIDATION_FAILED_MESSAGE = "请求参数不合法";
    private static final String GENERIC_INTERNAL_ERROR_MESSAGE = "服务器内部错误,请联系管理员并提供 traceId 排查";
    private static final String UPLOAD_TOO_LARGE_CODE = "UPLOAD_TOO_LARGE";
    private static final String UPLOAD_TOO_LARGE_MESSAGE = "上传文件超过大小上限";

    /** 业务异常已经是"预期内"的错误路径,由异常自身携带的 code / httpStatus / details 直接翻译成响应,不打 ERROR。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        log.warn(
                "业务异常被映射为 HTTP 响应, code={}, httpStatus={}, path={}, traceId={}",
                ex.code(),
                ex.httpStatus(),
                request.getRequestURI(),
                traceId);
        ErrorResponse body = ErrorResponse.of(ex.code(), ex.getMessage(), traceId, ex.details());
        return ResponseEntity.status(ex.httpStatus()).body(body);
    }

    /**
     * multipart 上传体积超过 {@code spring.servlet.multipart.max-file-size}(业务建模文档 5.2 七项上限之一)。
     * 这个异常在 {@code DispatcherServlet} 解析请求体阶段就抛出,早于任何 Controller 方法执行,不属于
     * {@link BusinessException} 体系,必须单独接一个分支——否则会落进兜底的 {@link Exception} 处理器,
     * 变成一个 500,而这明明是一次用户输错了(文件太大)的可预期错误,该给 4xx(参照
     * backend/CLAUDE.md 6.2:参数校验失败是 WARN,不是 ERROR)。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        log.warn("上传文件超过大小上限, path={}, traceId={}", request.getRequestURI(), traceId);
        ErrorResponse body = ErrorResponse.of(UPLOAD_TOO_LARGE_CODE, UPLOAD_TOO_LARGE_MESSAGE, traceId);
        return ResponseEntity.status(HTTP_STATUS_PAYLOAD_TOO_LARGE).body(body);
    }

    /** {@code @Valid} 校验 {@code @RequestBody} 失败,字段级错误进 details。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return handleValidationFailure(fieldErrors, request);
    }

    /**
     * Controller 方法上 {@code @RequestParam} / {@code @PathVariable} 直接带 JSR-380 注解校验失败。
     *
     * <p>Spring Framework 6.1 起,这类校验默认由 MVC 内建机制处理并抛这个类型,<b>不是</b>
     * {@code jakarta.validation.ConstraintViolationException}——那是 Spring Boot 3 时代的记忆,本项目基于
     * Spring Framework 7,已用 MockMvc 实测确认过真实抛出的类型(见 GlobalExceptionHandlerTest),不能凭旧版本经验
     * 假设。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, Object> violations = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameterName = result.getMethodParameter().getParameterName();
            String key = parameterName != null
                    ? parameterName
                    : "arg" + result.getMethodParameter().getParameterIndex();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                violations.put(key, error.getDefaultMessage());
            }
        }
        return handleValidationFailure(violations, request);
    }

    /** {@code @Validated} 标注的 application 层服务方法参数校验失败(AOP 方式),违规属性路径进 details。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> violations = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            violations.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return handleValidationFailure(violations, request);
    }

    /** 兜底分支:任何没被上面几条捕获的异常都是未预期的,必须 ERROR 且带栈,且不能把内部细节泄漏给客户端。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        // e 放最后一个参数、不占位符——写成 {} 传 e 会丢栈(backend/CLAUDE.md 6.3)。
        log.error("未预期异常, path={}, traceId={}", request.getRequestURI(), traceId, ex);
        ErrorResponse body =
                ErrorResponse.of(SystemErrorCode.INTERNAL_ERROR.name(), GENERIC_INTERNAL_ERROR_MESSAGE, traceId);
        return ResponseEntity.status(HTTP_STATUS_INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<ErrorResponse> handleValidationFailure(
            Map<String, Object> fieldErrors, HttpServletRequest request) {
        String traceId = resolveTraceId();
        // 参数校验失败是用户输错了,不是系统坏了:WARN,不是 ERROR(backend/CLAUDE.md 6.2)。
        log.warn(
                "请求参数校验失败, path={}, fieldErrorCount={}, traceId={}",
                request.getRequestURI(),
                fieldErrors.size(),
                traceId);
        ErrorResponse body = ErrorResponse.of(
                SystemErrorCode.VALIDATION_FAILED.name(), VALIDATION_FAILED_MESSAGE, traceId, fieldErrors);
        return ResponseEntity.status(HTTP_STATUS_BAD_REQUEST).body(body);
    }

    /**
     * 从 MDC 取 traceId。正常链路里 {@code TraceIdFilter} 保证每个请求都写好了 traceId,理论上总能取到;取不到说明
     * 过滤器链被跳过或提前抛出,是需要人介入排查的异常状态,因此这里用 ERROR 记录并生成兜底值,而不是静默返回
     * null/空串——否则错误响应体里的 traceId 字段会失去意义,用户报障时也没有东西可贴。
     */
    private String resolveTraceId() {
        String traceId = MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String fallbackTraceId = UUID.randomUUID().toString();
        log.error("MDC 中缺失 traceId,已生成兜底值, fallbackTraceId={}", fallbackTraceId);
        return fallbackTraceId;
    }
}
