package com.landray.skillhub.shared.exception;

/**
 * 通用的"权限不足"异常,统一映射为 HTTP 403。
 *
 * <p>与 {@link UnauthenticatedException} 的区别:这个异常发生在"已经知道你是谁"之后——已解析出合法会话,但
 * 该角色不满足接口要求(例如非 ADMIN 调用 ADMIN 专属接口)。放在 shared 而不是 domain,理由同
 * {@link ResourceNotFoundException}:权限校验是跨所有实体的通用语义,不属于任何单一领域概念自己的规则。
 */
public class AccessDeniedException extends BusinessException {

    private static final String CODE = "ACCESS_DENIED";
    private static final int HTTP_STATUS_FORBIDDEN = 403;

    public AccessDeniedException(String message) {
        super(CODE, message, HTTP_STATUS_FORBIDDEN);
    }
}
