package com.landray.skillhub.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.TraceIdConstants;

/**
 * 用 MockMvc 打真实请求验证 GlobalExceptionHandler,不 mock 被测对象自身。
 *
 * <p>{@link GlobalExceptionHandlerProbeController} 是仅供本测试使用的假控制器——本仓库目前还没有落地任何业务
 * Controller(Skill 相关领域模型还在搭建中),要验证异常映射链路只能自备一个会按需抛出各类异常的探针。
 * {@code @WebMvcTest} 默认会自动装配已发现的 {@code Filter} bean,所以 {@link TraceIdFilter} 在这些用例里会真实跑
 * 起来,traceId 链路是端到端验证的。
 *
 * <p>{@code server.servlet.context-path} 清空是因为切片测试仍会加载 {@code application-dev.yml}(默认激活 dev
 * profile),里面把 context-path 定死成 {@code /skillhub}——这里验证的是 GlobalExceptionHandler 本身的行为,与
 * context-path 无关,清空后测试路径不用平白多带一段前缀,也不会因为哪天 dev 的 context-path 改了而被带着遭殃。
 *
 * <p>{@code @MockitoBean AuthApplicationService} / {@code CurrentUserAccessor}:{@code @WebMvcTest} 会把全局的
 * {@code WebMvcConfigurer}(认证拦截器的注册配置)与实现了 {@code HandlerInterceptor} 的
 * {@code AuthenticationInterceptor} 一并装配进来,而它的构造函数依赖这两个 bean——这个探针本身不测认证
 * (探针接口都没标 {@code @RequireLogin}/{@code @RequireAdmin},拦截器对它们全部放行),但整个 web 层切片仍然
 * 需要这两个依赖才能起得来,mock 掉即可,不需要真实实现。
 */
@WebMvcTest(controllers = GlobalExceptionHandlerProbeController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    @MockitoBean
    private CurrentUserAccessor currentUserAccessor;

    @Test
    @DisplayName("BusinessException 子类携带 404 时,响应状态码、错误体与 traceId 都正确")
    void should_returnMappedStatusAndBody_when_resourceNotFoundExceptionThrown() throws Exception {
        mockMvc.perform(get("/test-support/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKILL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("skill 不存在"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.details.skillId").value(99))
                .andExpect(header().exists(TraceIdConstants.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("BusinessException 子类携带 409 时,响应体里的 traceId 与响应头一致")
    void should_returnConflictAndConsistentTraceId_when_resourceConflictExceptionThrown() throws Exception {
        mockMvc.perform(get("/test-support/conflict").header(TraceIdConstants.TRACE_ID_HEADER, "client-trace-007"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKILL_NAME_DUPLICATED"))
                .andExpect(jsonPath("$.traceId").value("client-trace-007"))
                .andExpect(header().string(TraceIdConstants.TRACE_ID_HEADER, "client-trace-007"));
    }

    @Test
    @DisplayName("请求体参数校验失败时,映射为 400、code 为 VALIDATION_FAILED,字段错误进 details")
    void should_returnBadRequestWithFieldErrors_when_requestBodyFailsValidation() throws Exception {
        mockMvc.perform(post("/test-support/validate")
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    @DisplayName("未预期异常时,映射为 500、code 为 INTERNAL_ERROR,且不泄漏原始异常消息")
    void should_returnInternalErrorWithGenericMessage_when_unexpectedExceptionThrown() throws Exception {
        mockMvc.perform(get("/test-support/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误,请联系管理员并提供 traceId 排查"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                // details 为空时必须整个字段消失,不是 null / {}——业务建模文档 6.1 "details 可选" 的字面含义,
                // 也顺带验证 @JsonInclude(NON_NULL) 真的生效(它来自 jackson-annotations,不是我们自己的代码)。
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("查询参数直接带校验注解失败时(HandlerMethodValidationException),同样映射为 400")
    void should_returnBadRequestWithFieldErrors_when_requestParamFailsValidation() throws Exception {
        mockMvc.perform(get("/test-support/validate-param").param("value", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.details.value").exists());
    }

    @Test
    @DisplayName("MDC 中没有 traceId 时,直接调用 handler 也不能静默返回 null 或空串,而是生成兜底值")
    void should_generateFallbackTraceId_when_mdcHasNoTraceId() {
        MDC.clear();
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-support/not-found");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(
                new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在"), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().traceId()).isNotBlank();
    }
}
