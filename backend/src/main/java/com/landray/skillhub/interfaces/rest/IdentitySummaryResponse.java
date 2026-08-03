package com.landray.skillhub.interfaces.rest;

import java.time.Instant;

import com.landray.skillhub.domain.model.UserIdentity;

/**
 * {@code GET /identities} / {@code GET /auth/me} 里一条身份的展示形状。
 *
 * <p>刻意带上 {@code externalId}(登录名)——业务建模文档 4.4:一个人可能有两个 LOCAL 身份,不返回登录名用户
 * 就看不出自己有哪几个本地账号。<b>不返回 {@code credential}</b>(哪怕是哈希也不返回,没有任何展示价值,
 * 多返回一个字段就多一分泄漏面)。
 */
public record IdentitySummaryResponse(
        long id, String provider, String externalId, String displayHint, Instant lastLoginAt, Instant createdAt) {

    public static IdentitySummaryResponse from(UserIdentity identity) {
        return new IdentitySummaryResponse(
                identity.id(),
                identity.provider().name(),
                identity.externalId(),
                identity.displayHint(),
                identity.lastLoginAt(),
                identity.createdAt());
    }
}
