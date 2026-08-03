package com.landray.skillhub.domain.model;

/**
 * {@code app_user.status}。MERGED 由 P3 的账号合并写入,本轮(P0)不会产生该状态的数据,
 * 但枚举值先建全,避免将来加账号合并时又要动这个类型(业务建模文档 4.3)。
 */
public enum AppUserStatus {
    ACTIVE,
    DISABLED,
    MERGED
}
