package com.landray.skillhub.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@link AppUser} 仓储接口,只有接口——实现见
 * {@code infrastructure.persistence.AppUserRepositoryImpl}。
 */
public interface AppUserRepository {

    /** 插入一个新用户,返回带生成 id 的实例。 */
    AppUser save(AppUser user);

    /** 全量更新可变字段(displayName / employeeNo / email / avatarUrl / role / status / mergedIntoUserId)。 */
    void update(AppUser user);

    Optional<AppUser> findById(long id);

    /** 批量查询,用于 admin 用户列表拼装每行的归属身份信息,避免逐条 N+1。 */
    List<AppUser> findByIds(Collection<Long> ids);

    /**
     * 分页查询用户列表,可选按 provider 过滤(只返回持有该 provider 身份的用户)。
     *
     * @param providerFilter 为 {@code null} 时不过滤
     */
    PageResult<AppUser> search(int page, int size, IdentityProvider providerFilter);
}
