package com.landray.skillhub.domain.model;

/**
 * {@code app_user.role}。只有两级,不做 RBAC(业务建模文档 4.3)。
 */
public enum UserRole {
    USER,
    ADMIN
}
