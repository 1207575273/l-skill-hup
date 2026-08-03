package com.landray.skillhub.domain.model;

/**
 * {@code user_identity.status}。解绑即 REVOKED,不物理删,保留审计线索(业务建模文档 4.4)。
 * 解绑本身是 P1 范围,P0 不会产生 REVOKED 数据,但登录校验要按这个状态防御式判断。
 */
public enum IdentityStatus {
    ACTIVE,
    REVOKED
}
