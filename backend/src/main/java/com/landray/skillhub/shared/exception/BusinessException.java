package com.landray.skillhub.shared.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务异常基类。
 *
 * <p>由 domain / application 层抛出,统一由 interfaces 层的 RestControllerAdvice 映射成 HTTP 响应。 禁止在 Controller
 * 里 try-catch 业务异常。
 *
 * <p>本类被 domain 依赖,因此必须保持纯 Java、零框架注解。HTTP 状态码故意用 {@code int} 表达,不用
 * {@code org.springframework.http.HttpStatus}——引入 Spring 类型会立刻被 ArchUnit 的 domain 纯净性规则拦下来;
 * 真正翻译成框架类型的动作留给 interfaces 层的 {@code GlobalExceptionHandler}(它直接把 int 传给
 * {@code ResponseEntity.status(int)})。
 */
public abstract class BusinessException extends RuntimeException {

    /** 未显式指定 httpStatus 时的默认值,对应"请求不满足业务规则"这一类最常见的场景。 */
    public static final int DEFAULT_HTTP_STATUS = 400;

    private final String code;
    private final int httpStatus;
    private final Map<String, Object> details;

    protected BusinessException(String code, String message) {
        this(code, message, DEFAULT_HTTP_STATUS, null);
    }

    protected BusinessException(String code, String message, int httpStatus) {
        this(code, message, httpStatus, null);
    }

    protected BusinessException(String code, String message, int httpStatus, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = copyOf(details);
    }

    private static Map<String, Object> copyOf(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /** 业务错误码,前端据此做差异化处理,不要依赖 message 文本。 */
    public String code() {
        return code;
    }

    /** HTTP 状态码。默认 400,重名冲突、资源不存在等场景由具体子类(见 shared.exception 下的通用子类)指定别的值。 */
    public int httpStatus() {
        return httpStatus;
    }

    /** 错误详情,承载错误响应体里可选的 details 字段。永不为 null,默认空表且不可变。 */
    public Map<String, Object> details() {
        return details;
    }
}
