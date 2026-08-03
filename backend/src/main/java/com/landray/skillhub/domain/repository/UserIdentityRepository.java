package com.landray.skillhub.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.model.UserIdentity;

/**
 * {@link UserIdentity} 仓储接口,只有接口——实现见
 * {@code infrastructure.persistence.UserIdentityRepositoryImpl}。
 */
public interface UserIdentityRepository {

    /**
     * 插入一个新身份,返回带生成 id 的实例。
     *
     * <p>{@code (provider, external_id)} 唯一索引冲突时,实现必须捕获 {@code DuplicateKeyException} 并映射成
     * {@code ResourceConflictException},不做先查后插(业务建模文档 5.14)。
     */
    UserIdentity save(UserIdentity identity);

    /** 全量更新可变字段(credential / displayHint / failedLoginCount / lockedUntil / lastLoginAt / status)。 */
    void update(UserIdentity identity);

    Optional<UserIdentity> findById(long id);

    Optional<UserIdentity> findByProviderAndExternalId(IdentityProvider provider, String externalId);

    /** 一个人持有的全部登录方式,用于 {@code GET /identities}。 */
    List<UserIdentity> findByUserId(long userId);

    /** 批量查询,用于 admin 用户列表拼装每行持有的 provider 集合,避免逐条 N+1。 */
    List<UserIdentity> findByUserIds(Collection<Long> userIds);
}
