package com.landray.skillhub.application.auth;

import com.landray.skillhub.domain.model.UserRole;

/**
 * 一次请求里"当前是谁"的解析结果:由 {@link AuthApplicationService#resolveSession(String)} 产出,供
 * interfaces 层的认证拦截器与 Controller 使用。
 *
 * <p>不放在 domain——它不是持久化实体,只是"解析会话"这个用例的输出形状,天然属于 application。
 */
public record AuthenticatedUser(long userId, UserRole role, long identityId, long sessionId) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
