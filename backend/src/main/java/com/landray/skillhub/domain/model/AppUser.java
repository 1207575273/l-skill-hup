package com.landray.skillhub.domain.model;

import java.time.Instant;

/**
 * 一个人。业务归属的锚点,**不含任何登录方式**——登录方式全在 {@link UserIdentity}(业务建模文档 3.1、4.3)。
 *
 * <p>不可变记录,状态变更(禁用/恢复)通过返回新实例的方法表达,与 {@link UserIdentity} 同构,不做贫血的
 * getter/setter 堆砌。{@code id} 在插入前为 {@code null},由仓储实现回填数据库生成的自增 id。
 */
public record AppUser(
        Long id,
        String displayName,
        String employeeNo,
        String email,
        String avatarUrl,
        UserRole role,
        AppUserStatus status,
        Long mergedIntoUserId,
        Instant createdAt,
        Instant updatedAt) {

    /** 展示名列宽上限(VARCHAR(64)),超长在 interfaces 层的 Bean Validation 拦截,这里只做兜底防御。 */
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;

    /**
     * 内置 system 账号的 id。
     *
     * <p>V0002 迁移里它是全库第一条插入,所以必然是 1;那次迁移的注释把这个前提写明了,这里的硬编码不是巧合。
     * 它刻意不持有任何 {@link UserIdentity}、永远登不进来,存在的理由是让 {@code owner_user_id} /
     * {@code created_by_user_id} 这类归属列保持 NOT NULL —— 系统内置、导入、以及匿名上传的数据都挂在它名下,
     * 比把归属列做成可空再全链路特判要简单得多。
     *
     * <p>于是"归属人是 system"在业务上就等于**无主**:见 {@link Skill#isUnclaimed()}。
     */
    public static final long SYSTEM_USER_ID = 1L;

    public AppUser {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName 不能为空");
        }
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "displayName 长度不得超过 " + MAX_DISPLAY_NAME_LENGTH + " 个字符,实际 " + displayName.length());
        }
        if (role == null) {
            throw new IllegalArgumentException("role 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
    }

    /** 新建一个尚未持久化的用户(id 为 null,由仓储插入后回填),状态恒为 ACTIVE。 */
    public static AppUser newUser(String displayName, UserRole role) {
        Instant now = Instant.now();
        return new AppUser(null, displayName, null, null, null, role, AppUserStatus.ACTIVE, null, now, now);
    }

    public boolean isActive() {
        return status == AppUserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** 禁用,原地返回新实例;级联撤销会话由 application 层在同一事务内处理(业务建模文档 5.15)。 */
    public AppUser disable() {
        return new AppUser(
                id, displayName, employeeNo, email, avatarUrl, role, AppUserStatus.DISABLED, mergedIntoUserId,
                createdAt, updatedAt);
    }

    public AppUser enable() {
        return new AppUser(
                id, displayName, employeeNo, email, avatarUrl, role, AppUserStatus.ACTIVE, mergedIntoUserId,
                createdAt, updatedAt);
    }
}
