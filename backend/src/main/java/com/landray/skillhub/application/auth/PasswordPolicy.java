package com.landray.skillhub.application.auth;

import com.landray.skillhub.domain.exception.InvalidPasswordException;

/**
 * 密码长度策略,{@link AuthApplicationService} 与 {@link UserManagementApplicationService} 共用——两处都要在
 * hash 之前校验原始密码,抽成一处避免两份策略慢慢漂移出不同的数值。
 *
 * <p>下限 8 上限 72(BCrypt 有效输入长度)是本轮实现按常识定的策略,业务建模文档没有给出具体数值,详见
 * {@link com.landray.skillhub.domain.exception.InvalidPasswordException} 的类注释。
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 72;

    private PasswordPolicy() {}

    static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new InvalidPasswordException("密码长度不得少于 " + MIN_LENGTH + " 个字符");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw new InvalidPasswordException("密码长度不得超过 " + MAX_LENGTH + " 个字符");
        }
    }
}
