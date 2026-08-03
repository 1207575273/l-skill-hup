package com.landray.skillhub.infrastructure.persistence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.domain.repository.AppUserRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.AppUserMapper;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@link AppUserRepository} 的 MyBatis 实现。
 *
 * <p>{@link #save(AppUser)} 用 {@link Map} 作为插入参数,而不是直接把 {@link AppUser} 记录传给 MyBatis——见
 * {@code AppUserMapper.xml} 顶部注释:{@code useGeneratedKeys} 需要把生成的 id 写回参数对象,而 {@link AppUser}
 * 是不可变 record 没有 setter,写不回去。插入后用返回的 id 重新拼一个带真实 id 的新实例返回给调用方。
 */
@Repository
public class AppUserRepositoryImpl implements AppUserRepository {

    private final AppUserMapper appUserMapper;

    public AppUserRepositoryImpl(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    public AppUser save(AppUser user) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("displayName", user.displayName());
        params.put("employeeNo", user.employeeNo());
        params.put("email", user.email());
        params.put("avatarUrl", user.avatarUrl());
        params.put("role", user.role());
        params.put("status", user.status());
        params.put("mergedIntoUserId", user.mergedIntoUserId());

        appUserMapper.insert(params);

        Long generatedId = (Long) params.get("id");
        return new AppUser(
                generatedId,
                user.displayName(),
                user.employeeNo(),
                user.email(),
                user.avatarUrl(),
                user.role(),
                user.status(),
                user.mergedIntoUserId(),
                user.createdAt(),
                user.updatedAt());
    }

    @Override
    public void update(AppUser user) {
        appUserMapper.update(user);
    }

    @Override
    public Optional<AppUser> findById(long id) {
        return Optional.ofNullable(appUserMapper.selectById(id));
    }

    @Override
    public List<AppUser> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return appUserMapper.selectByIds(ids);
    }

    @Override
    public PageResult<AppUser> search(int page, int size, IdentityProvider providerFilter) {
        String providerCode = providerFilter == null ? null : providerFilter.name();
        int offset = (page - 1) * size;
        List<AppUser> items = appUserMapper.selectPage(providerCode, offset, size);
        long total = appUserMapper.countAll(providerCode);
        return new PageResult<>(items, total, page, size);
    }
}
