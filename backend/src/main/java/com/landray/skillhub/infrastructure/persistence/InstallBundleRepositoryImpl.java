package com.landray.skillhub.infrastructure.persistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.ClientCode;
import com.landray.skillhub.domain.model.InstallBundle;
import com.landray.skillhub.domain.model.InstallScope;
import com.landray.skillhub.domain.repository.InstallBundleRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.InstallBundleMapper;
import com.landray.skillhub.infrastructure.persistence.po.InstallBundleRow;
import com.landray.skillhub.shared.exception.ResourceConflictException;

/**
 * {@link InstallBundleRepository} 的 MyBatis 实现。
 *
 * <p>{@link #save(InstallBundle)} 捕获 {@code uk_install_bundle_token} 唯一索引冲突并映射成业务错误码
 * ——token 是 144 位随机数,碰撞概率近乎为零,但"近乎为零"不是"不会发生",唯一索引仍是仲裁者,不做先查后插
 * (业务建模文档 5.14 的通用原则)。
 */
@Repository
public class InstallBundleRepositoryImpl implements InstallBundleRepository {

    private static final String TOKEN_CONSTRAINT = "uk_install_bundle_token";

    private final InstallBundleMapper installBundleMapper;

    public InstallBundleRepositoryImpl(InstallBundleMapper installBundleMapper) {
        this.installBundleMapper = installBundleMapper;
    }

    @Override
    public InstallBundle save(InstallBundle bundle) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("token", bundle.token());
        params.put("items", bundle.items());
        params.put("client", bundle.client().apiCode());
        params.put("scope", bundle.scope().apiCode());
        params.put("createdByUserId", bundle.createdByUserId());
        params.put("expiresAt", bundle.expiresAt());

        try {
            installBundleMapper.insert(params);
        } catch (DuplicateKeyException e) {
            throw mapDuplicateKey(e);
        }

        Long generatedId = (Long) params.get("id");
        return new InstallBundle(
                generatedId,
                bundle.token(),
                bundle.items(),
                bundle.client(),
                bundle.scope(),
                bundle.createdByUserId(),
                bundle.createdAt(),
                bundle.expiresAt());
    }

    private ResourceConflictException mapDuplicateKey(DuplicateKeyException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains(TOKEN_CONSTRAINT)) {
            return new ResourceConflictException("INSTALL_BUNDLE_TOKEN_CONFLICT", "安装单 token 生成冲突,请重新生成");
        }
        return new ResourceConflictException("INSTALL_BUNDLE_SAVE_CONFLICT", "安装单写入冲突: " + message);
    }

    @Override
    public Optional<InstallBundle> findByToken(String token) {
        InstallBundleRow row = installBundleMapper.selectByToken(token);
        return Optional.ofNullable(row).map(InstallBundleRepositoryImpl::toDomain);
    }

    @Override
    public int deleteExpiredBefore(Instant threshold) {
        return installBundleMapper.deleteExpiredBefore(threshold);
    }

    private static InstallBundle toDomain(InstallBundleRow row) {
        ClientCode client = ClientCode.fromApiCode(row.client())
                .orElseThrow(() -> new IllegalStateException(
                        "install_bundle.client 存了未识别的值,数据已损坏, client=" + row.client()));
        InstallScope scope = InstallScope.fromApiCode(row.scope())
                .orElseThrow(() -> new IllegalStateException(
                        "install_bundle.scope 存了未识别的值,数据已损坏, scope=" + row.scope()));
        return new InstallBundle(
                row.id(), row.token(), row.items(), client, scope, row.createdByUserId(), row.createdAt(),
                row.expiresAt());
    }
}
