package com.landray.skillhub.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * 一种登录方式。LOCAL 与 DINGTALK 完全对称,不存在主副之分(业务建模文档 3.1、4.4)。
 *
 * <p>登录失败限流规则挂在这里而不是 {@link AppUser} 上:密码被撞库不该连带锁死这个人的钉钉登录
 * (文档 4.4 原话)。{@link #recordLoginFailure(Instant)} / {@link #recordLoginSuccess(Instant)}
 * 是这张实体真正带业务规则的地方,不是贫血对象。
 */
public record UserIdentity(
        Long id,
        Long userId,
        IdentityProvider provider,
        String externalId,
        String credential,
        String displayHint,
        int failedLoginCount,
        Instant lockedUntil,
        Instant lastLoginAt,
        IdentityStatus status,
        Instant createdAt,
        Instant updatedAt) {

    /** 连续失败 5 次触发锁定(业务建模文档 3.2、4.4)。 */
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    /** 锁定时长 15 分钟(业务建模文档 3.2、4.4)。 */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    public UserIdentity {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
    }

    /** 新建一个尚未持久化的 LOCAL 身份(id 为 null),{@code passwordHash} 由 domain.auth.PasswordHasher 算好后传入。 */
    public static UserIdentity newLocal(Long userId, String login, String passwordHash) {
        Instant now = Instant.now();
        return new UserIdentity(
                null, userId, IdentityProvider.LOCAL, login, passwordHash, null, 0, null, null,
                IdentityStatus.ACTIVE, now, now);
    }

    public boolean isActive() {
        return status == IdentityStatus.ACTIVE;
    }

    /** 是否处于锁定期内。锁定期内即使密码正确也必须拒绝登录(业务建模文档 3.2)。 */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * 记一次登录成功:清零失败计数与锁定,更新最近登录时间。
     *
     * <p>不在锁定期内调用——锁定期内的登录尝试在 application 层被 {@link #isLocked(Instant)} 短路拦截,根本不会
     * 走到密码校验这一步,自然也不会调用到这个方法。
     */
    public UserIdentity recordLoginSuccess(Instant now) {
        return new UserIdentity(
                id, userId, provider, externalId, credential, displayHint, 0, null, now, status, createdAt, now);
    }

    /**
     * 记一次登录失败:失败计数 +1,达到阈值时设置锁定到期时间。
     *
     * <p>只在"未处于锁定状态"时被调用——已锁定时 application 层直接短路拒绝,不会重复调用本方法延长锁定期
     * （否则持续攻击可以让账号被永久锁死,那本身就是一种拒绝服务)。
     */
    public UserIdentity recordLoginFailure(Instant now) {
        int nextFailedCount = failedLoginCount + 1;
        Instant nextLockedUntil = nextFailedCount >= MAX_FAILED_LOGIN_ATTEMPTS ? now.plus(LOCK_DURATION) : lockedUntil;
        return new UserIdentity(
                id, userId, provider, externalId, credential, displayHint, nextFailedCount, nextLockedUntil,
                lastLoginAt, status, createdAt, now);
    }

    /** 改密码:只换 credential,不动登录失败计数与锁定状态。 */
    public UserIdentity changeCredential(String newCredentialHash) {
        return new UserIdentity(
                id, userId, provider, externalId, newCredentialHash, displayHint, failedLoginCount, lockedUntil,
                lastLoginAt, status, createdAt, Instant.now());
    }
}
