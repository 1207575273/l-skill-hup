package com.landray.skillhub.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.landray.skillhub.shared.logging.TraceIdConstants;

/**
 * 直接调用 {@link TraceIdFilter#doFilter} 做纯单测,不起 Spring 容器——更快、更聚焦,且不依赖 MockMvc 的过滤器装配时机。
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdcDefensively() {
        // 防止某个用例断言失败时提前退出、没走到 filter 的 finally,污染后续用例的 MDC。
        MDC.clear();
    }

    @Test
    @DisplayName("请求头带 X-Trace-Id 时,复用上游值,不再自己生成")
    void should_reuseUpstreamTraceId_when_requestHeaderPresent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        request.addHeader(TraceIdConstants.TRACE_ID_HEADER, "upstream-trace-id-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(TraceIdConstants.TRACE_ID_HEADER)).isEqualTo("upstream-trace-id-001");
    }

    @Test
    @DisplayName("请求头不带 X-Trace-Id 时,自动生成一个非空的 traceId")
    void should_generateTraceId_when_requestHeaderAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(TraceIdConstants.TRACE_ID_HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("上游 traceId 含控制字符时,清洗后回写响应头——防止日志注入伪造整行假日志")
    void should_stripControlCharacters_when_upstreamTraceIdContainsThem() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        request.addHeader(TraceIdConstants.TRACE_ID_HEADER, "abc\r\ndef");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(TraceIdConstants.TRACE_ID_HEADER)).isEqualTo("abcdef");
    }

    @Test
    @DisplayName("上游 traceId 清洗后为空(纯控制字符)时,退化为自动生成")
    void should_generateTraceId_when_upstreamTraceIdIsOnlyControlCharacters() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        request.addHeader(TraceIdConstants.TRACE_ID_HEADER, "\r\n");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(TraceIdConstants.TRACE_ID_HEADER)).isNotBlank();
    }

    @Test
    @DisplayName("请求处理期间,MDC 里能读到同一个 traceId")
    void should_populateMdc_when_chainIsExecuting() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY))
                .isEqualTo(((MockHttpServletResponse) res).getHeader(TraceIdConstants.TRACE_ID_HEADER)));
    }

    @Test
    @DisplayName("请求正常结束后,MDC 必须被清空——不清会随线程池复用串到下一个请求上")
    void should_clearMdc_when_requestCompletesNormally() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("下游抛异常时,MDC 也必须被清空——依赖 finally,不能只在正常路径清")
    void should_clearMdc_when_downstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                    throw new ServletException("模拟下游抛异常");
                }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)).isNull();
    }
}
