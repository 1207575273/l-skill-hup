package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.model.InstallItem;

/**
 * {@code install_bundle} 的扁平查询结果行。
 *
 * <p>{@code client} / {@code scope} 刻意用 {@code String} 而不是直接映射成
 * {@link com.landray.skillhub.domain.model.ClientCode} / {@link com.landray.skillhub.domain.model.InstallScope}
 * ——数据库列存的是小写字符串(如 {@code claude} / {@code global},业务建模文档 4.13),而 Java 枚举常量按惯例
 * 大写命名,MyBatis 默认的 {@code EnumTypeHandler} 按 {@code Enum.valueOf} 精确匹配大小写会直接失败。这属于
 * "表结构确实跟领域模型对不上"的情形(backend/CLAUDE.md 第二节允许在 infrastructure 引入 PO 的唯一理由),转换在
 * {@code InstallBundleRepositoryImpl} 里做。
 */
public record InstallBundleRow(
        Long id,
        String token,
        List<InstallItem> items,
        String client,
        String scope,
        Long createdByUserId,
        Instant createdAt,
        Instant expiresAt) {}
