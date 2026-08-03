package com.landray.skillhub.infrastructure.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.UserSession;
import com.landray.skillhub.domain.repository.UserSessionRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.UserSessionMapper;

/** {@link UserSessionRepository} 的 MyBatis 实现。 */
@Repository
public class UserSessionRepositoryImpl implements UserSessionRepository {

    private final UserSessionMapper userSessionMapper;

    public UserSessionRepositoryImpl(UserSessionMapper userSessionMapper) {
        this.userSessionMapper = userSessionMapper;
    }

    @Override
    public UserSession save(UserSession session) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tokenHash", session.tokenHash());
        params.put("userId", session.userId());
        params.put("identityId", session.identityId());
        params.put("userAgent", session.userAgent());
        params.put("clientIp", session.clientIp());
        params.put("expiresAt", session.expiresAt());

        userSessionMapper.insert(params);

        Long generatedId = (Long) params.get("id");
        return new UserSession(
                generatedId,
                session.tokenHash(),
                session.userId(),
                session.identityId(),
                session.userAgent(),
                session.clientIp(),
                session.createdAt(),
                session.expiresAt(),
                session.revokedAt());
    }

    @Override
    public Optional<UserSession> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(userSessionMapper.selectByTokenHash(tokenHash));
    }

    @Override
    public void revoke(long sessionId, Instant revokedAt) {
        userSessionMapper.revokeById(sessionId, revokedAt);
    }

    @Override
    public int revokeAllActiveByUserId(long userId, Instant revokedAt) {
        return userSessionMapper.revokeAllActiveByUserId(userId, revokedAt);
    }
}
