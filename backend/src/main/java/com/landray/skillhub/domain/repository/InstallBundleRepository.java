package com.landray.skillhub.domain.repository;

import java.time.Instant;
import java.util.Optional;

import com.landray.skillhub.domain.model.InstallBundle;

/** {@code install_bundle} 的仓储接口。只有接口,纯 Java,实现在 {@code infrastructure.persistence}。 */
public interface InstallBundleRepository {

    /** 持久化一个新安装单,返回带生成 id 的实例。token 冲突(概率上近乎不可能,仍需仲裁)由实现映射成业务异常。 */
    InstallBundle save(InstallBundle bundle);

    Optional<InstallBundle> findByToken(String token);

    /**
     * 删除过期超过阈值的安装单,返回删除行数。业务建模文档 4.13:每次 {@code POST /bundles} 顺手清理,
     * 避免这张表无限增长。
     */
    int deleteExpiredBefore(Instant threshold);
}
