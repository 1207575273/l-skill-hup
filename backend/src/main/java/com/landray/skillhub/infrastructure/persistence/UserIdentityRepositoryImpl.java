package com.landray.skillhub.infrastructure.persistence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.UserIdentity;
import com.landray.skillhub.domain.repository.UserIdentityRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.UserIdentityMapper;
import com.landray.skillhub.shared.exception.ResourceConflictException;

/**
 * {@link UserIdentityRepository} 的 MyBatis 实现。
 *
 * <p>{@link #save(UserIdentity)} 捕获 {@code uk_user_identity_external} 唯一索引冲突并映射成业务错误码,不做
 * 先查后插——两人同时用同一个登录名注册,唯一索引才是仲裁者(业务建模文档 5.14)。
 */
@Repository
public class UserIdentityRepositoryImpl implements UserIdentityRepository {

    private static final String EXTERNAL_ID_CONSTRAINT = "uk_user_identity_external";

    private final UserIdentityMapper userIdentityMapper;

    public UserIdentityRepositoryImpl(UserIdentityMapper userIdentityMapper) {
        this.userIdentityMapper = userIdentityMapper;
    }

    @Override
    public UserIdentity save(UserIdentity identity) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", identity.userId());
        params.put("provider", identity.provider());
        params.put("externalId", identity.externalId());
        params.put("credential", identity.credential());
        params.put("displayHint", identity.displayHint());
        params.put("failedLoginCount", identity.failedLoginCount());
        params.put("lockedUntil", identity.lockedUntil());
        params.put("lastLoginAt", identity.lastLoginAt());
        params.put("status", identity.status());

        try {
            userIdentityMapper.insert(params);
        } catch (DuplicateKeyException e) {
            throw mapDuplicateKey(e, identity);
        }

        Long generatedId = (Long) params.get("id");
        return new UserIdentity(
                generatedId,
                identity.userId(),
                identity.provider(),
                identity.externalId(),
                identity.credential(),
                identity.displayHint(),
                identity.failedLoginCount(),
                identity.lockedUntil(),
                identity.lastLoginAt(),
                identity.status(),
                identity.createdAt(),
                identity.updatedAt());
    }

    private ResourceConflictException mapDuplicateKey(DuplicateKeyException e, UserIdentity identity) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains(EXTERNAL_ID_CONSTRAINT)) {
            return new ResourceConflictException(
                    "IDENTITY_EXTERNAL_ID_DUPLICATED",
                    "登录名已被占用: " + identity.externalId(),
                    Map.of("provider", identity.provider().name(), "externalId", identity.externalId()));
        }
        // 未识别的约束名——不要吞掉,包一层业务异常但保留原始信息,方便排查是不是新加了约束却漏了映射分支。
        return new ResourceConflictException("IDENTITY_SAVE_CONFLICT", "身份信息写入冲突: " + message);
    }

    @Override
    public void update(UserIdentity identity) {
        userIdentityMapper.update(identity);
    }

    @Override
    public Optional<UserIdentity> findById(long id) {
        return Optional.ofNullable(userIdentityMapper.selectById(id));
    }

    @Override
    public Optional<UserIdentity> findByProviderAndExternalId(IdentityProvider provider, String externalId) {
        return Optional.ofNullable(userIdentityMapper.selectByProviderAndExternalId(provider.name(), externalId));
    }

    @Override
    public List<UserIdentity> findByUserId(long userId) {
        return userIdentityMapper.selectByUserId(userId);
    }

    @Override
    public List<UserIdentity> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIdentityMapper.selectByUserIds(userIds);
    }
}
