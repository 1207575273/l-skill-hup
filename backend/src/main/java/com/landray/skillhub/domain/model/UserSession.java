package com.landray.skillhub.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * 一次登录会话。不透明 token,可服务端撤销(业务建模文档 4.5)。
 *
 * <p>同时记 {@code userId} 与 {@code identityId} 是刻意冗余:鉴权只用 {@code userId},而"这次是密码登的还是钉钉
 * 登的"在安全审计与"改当前身份密码"(4.4)时都要用到 {@code identityId}。
 */
public record UserSession(
        Long id,
        String tokenHash,
        Long userId,
        Long identityId,
        String userAgent,
        String clientIp,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt) {

    /** 会话有效期,默认签发后 7 天(业务建模文档 4.5)。 */
    public static final Duration DEFAULT_TTL = Duration.ofDays(7);

    public UserSession {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (identityId == null) {
            throw new IllegalArgumentException("identityId 不能为空");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt 不能为空");
        }
    }

    /** 签发一个新会话(id 为 null,由仓储插入后回填),默认 7 天后过期。 */
    public static UserSession issue(
            Long userId, Long identityId, SessionToken token, String userAgent, String clientIp, Instant now) {
        return new UserSession(
                null, token.hash(), userId, identityId, userAgent, clientIp, now, now.plus(DEFAULT_TTL), null);
    }

    /** 未被撤销且未过期才算有效;过期与已撤销在这里统一判定,调用方不需要分别处理两种"失效"的语义差异。 */
    public boolean isValid(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
