package com.landray.skillhub.application.auth;

import java.time.Instant;

import com.landray.skillhub.domain.model.AppUser;

/** 登录 / 注册成功后的用例输出:明文 token 只在这里出现一次,之后库里只存哈希。 */
public record LoginResult(String token, Instant expiresAt, AppUser user, long identityId) {}
