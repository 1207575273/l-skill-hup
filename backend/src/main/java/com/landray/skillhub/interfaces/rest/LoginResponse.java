package com.landray.skillhub.interfaces.rest;

import java.time.Instant;

import com.landray.skillhub.application.auth.LoginResult;
import com.landray.skillhub.domain.model.UserRole;

/**
 * {@code POST /auth/login} / {@code POST /auth/register} 响应体。
 *
 * <p>顺手带上基础用户信息(不止 token),前端登录后不需要立刻再调一次 {@code GET /auth/me} 才能展示"你好,张三"。
 */
public record LoginResponse(String token, Instant expiresAt, long userId, String displayName, UserRole role) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.token(),
                result.expiresAt(),
                result.user().id(),
                result.user().displayName(),
                result.user().role());
    }
}
