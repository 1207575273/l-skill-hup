package com.landray.skillhub.interfaces.rest;

/**
 * GlobalExceptionHandler 里由框架异常(而非 {@code BusinessException})直接产生的错误码。
 *
 * <p>业务异常的 code 由各业务异常自带(如 {@code SKILL_NAME_INVALID}),这里只覆盖"压根没走到业务逻辑就失败"的两种
 * 通用场景:参数校验、未预期异常。稳定不变,前端可以按值分支。
 */
public enum SystemErrorCode {
    VALIDATION_FAILED,
    INTERNAL_ERROR
}
