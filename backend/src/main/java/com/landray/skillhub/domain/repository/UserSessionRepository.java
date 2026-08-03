package com.landray.skillhub.domain.repository;

import java.time.Instant;
import java.util.Optional;

import com.landray.skillhub.domain.model.UserSession;

/**
 * {@link UserSession} 仓储接口,只有接口——实现见
 * {@code infrastructure.persistence.UserSessionRepositoryImpl}。
 */
public interface UserSessionRepository {

    /** 插入一个新会话,返回带生成 id 的实例。 */
    UserSession save(UserSession session);

    Optional<UserSession> findByTokenHash(String tokenHash);

    /** 撤销单个会话(登出),幂等——重复撤销不报错。 */
    void revoke(long sessionId, Instant revokedAt);

    /**
     * 撤销某个用户名下全部尚未失效的会话(禁用用户时踢下线,业务建模文档 5.15)。
     *
     * @return 实际撤销的行数,调用方用于日志留痕
     */
    int revokeAllActiveByUserId(long userId, Instant revokedAt);
}
