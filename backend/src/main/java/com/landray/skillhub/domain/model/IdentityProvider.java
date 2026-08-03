package com.landray.skillhub.domain.model;

/**
 * {@code user_identity.provider}。LOCAL 与 DINGTALK 完全对称,加新登录方式只加枚举值,不改表
 * (业务建模文档 3.1、4.4)。P0 只实现 LOCAL,DINGTALK 排 P1。
 */
public enum IdentityProvider {
    LOCAL,
    DINGTALK
}
