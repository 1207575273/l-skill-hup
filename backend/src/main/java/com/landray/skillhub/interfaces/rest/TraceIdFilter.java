package com.landray.skillhub.interfaces.rest;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.landray.skillhub.shared.logging.TraceIdConstants;

/**
 * 每个请求分配一个 traceId,放进 MDC 供全链路日志使用,响应结束前回写响应头,请求结束清空 MDC。
 *
 * <p>刻意放在 interfaces 层而不是 infrastructure/config,两个理由:
 *
 * <ol>
 *   <li>它做的事是"把外部 HTTP 请求头翻译成内部约定(MDC)、再把内部状态翻译回 HTTP 响应头",这正是 interfaces
 *       层的职责——同 Controller、GlobalExceptionHandler 一样站在系统的 HTTP 边界上,不是纯技术配置;
 *   <li>{@code pom.xml} 里 JaCoCo 把 {@code infrastructure/config/**} 整包排除出覆盖率统计,注释写的理由是"启动类与
 *       Spring 配置类无业务逻辑,测不到属正常"。这个类恰恰相反:有真实分支(复用/生成 traceId、清洗控制字符、
 *       finally 清 MDC)且完全可单测。放进那个包会让这些分支被那条已有的整包排除规则顺手带走,与"精确排除、
 *       不许整包关闭"的项目规矩相悖,所以放在不受那条排除规则影响的 interfaces 层。
 * </ol>
 *
 * <p>{@link Ordered#HIGHEST_PRECEDENCE} 保证它是过滤器链里最外层的一环——同一请求内的所有日志都要能拿到同一个
 * traceId,必须尽可能早地写进 MDC。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TraceIdConstants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TraceIdConstants.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 不清会随线程池复用串到下一个请求上,这类串号极难排查(backend/CLAUDE.md 6.4)。
            MDC.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String upstreamTraceId = sanitize(request.getHeader(TraceIdConstants.TRACE_ID_HEADER));
        return upstreamTraceId.isBlank() ? UUID.randomUUID().toString() : upstreamTraceId;
    }

    /** 上游传入的 traceId 是用户可控字符串,写入日志/回写响应头前必须去掉控制字符,否则能伪造出整行假日志(6.5)。 */
    private String sanitize(String rawTraceId) {
        if (rawTraceId == null) {
            return "";
        }
        return rawTraceId.replaceAll("[\\r\\n\\p{Cntrl}]", "").trim();
    }
}
