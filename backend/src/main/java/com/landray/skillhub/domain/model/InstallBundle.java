package com.landray.skillhub.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.exception.InvalidInstallBundleException;

/**
 * 一次勾选生成的安装单(业务建模文档 4.13)。
 *
 * <p>{@code createdByUserId} 可空——匿名用户也能生成安装单(甩链接进群聊不该被登录墙拦住)。
 */
public record InstallBundle(
        Long id,
        String token,
        List<InstallItem> items,
        ClientCode client,
        InstallScope scope,
        Long createdByUserId,
        Instant createdAt,
        Instant expiresAt) {

    /**
     * 单次安装单最多容纳的 skill 数——业务建模文档未给出具体数值,这是本轮实现按常识定的上限,防止极端请求
     * 生成一个包含成百上千个下载项的脚本(既没有实际使用场景,又会让渲染出的脚本失去可读性)。
     */
    public static final int MAX_ITEMS = 50;

    public InstallBundle {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw new InvalidInstallBundleException("安装单至少需要包含一个 skill");
        }
        if (items.size() > MAX_ITEMS) {
            throw new InvalidInstallBundleException(
                    "安装单最多包含 " + MAX_ITEMS + " 个 skill,实际 " + items.size());
        }
        if (client == null) {
            throw new IllegalArgumentException("client 不能为空");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope 不能为空");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt 不能为空");
        }
        items = List.copyOf(items);
    }

    /** 生成一个新安装单(id 为 null,由仓储插入后回填),token 随机生成,有效期由调用方传入的 ttl 决定。 */
    public static InstallBundle create(
            List<InstallItem> items, ClientCode client, InstallScope scope, Long createdByUserId, Instant now,
            Duration ttl) {
        return new InstallBundle(
                null,
                InstallBundleTokenGenerator.generate(),
                items,
                client,
                scope,
                createdByUserId,
                now,
                now.plus(ttl));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
