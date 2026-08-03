package com.landray.skillhub.shared.logging;

/**
 * traceId 相关的固定名字:MDC key 与 HTTP 头名。
 *
 * <p>写入方(interfaces 层的 {@code TraceIdFilter})与读取方(interfaces 层的 {@code GlobalExceptionHandler})分处
 * 两个类,却必须用同一个 key——抽成常量而不是各写各的字面量,避免有一天两边改名改岔了却没人发现,那就会变成"错误响应体
 * 里的 traceId 突然消失了"这种极难定位的问题。
 *
 * <p>MDC key 固定为 {@code traceId}(小驼峰),与 {@code application-dev.yml} / {@code application-prod.yml} 里控制台
 * 日志 pattern 的 {@code %X{traceId:-...}} 占位符一致,也是 backend/CLAUDE.md 6.4 固定 key 表登记的第一个 key。
 */
public final class TraceIdConstants {

    public static final String TRACE_ID_MDC_KEY = "traceId";

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceIdConstants() {}
}
