package com.landray.skillhub.interfaces.rest;

/**
 * GlobalExceptionHandler 里由框架异常(而非 {@code BusinessException})直接产生的错误码。
 *
 * <p>业务异常的 code 由各业务异常自带(如 {@code SKILL_NAME_INVALID}),这里只覆盖"压根没走到业务逻辑就失败"的三种
 * 通用场景:参数校验、请求本身不合法、未预期异常。稳定不变,前端可以按值分支。
 */
public enum SystemErrorCode {
    /** 业务参数校验失败(`@Valid` / 约束注解),响应体带 {@code details} 指明是哪几个字段出错。 */
    VALIDATION_FAILED,
    /**
     * 请求在进入 Controller 之前就被判定为不合法:缺必填参数、请求体不是合法 JSON、路径参数类型不匹配、
     * HTTP 方法不支持、路径不存在。
     *
     * <p>与 {@link #VALIDATION_FAILED} 分开是因为**能给出的信息量不同**:那个能精确到字段,这个只能说
     * "请求写错了"——它们在 Controller 方法执行之前抛出,压根还没有"字段"这个概念。合并成一个码会让前端
     * 以为总能拿到 {@code details}。
     */
    BAD_REQUEST,
    INTERNAL_ERROR
}
