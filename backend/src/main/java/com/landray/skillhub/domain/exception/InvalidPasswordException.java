package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 密码不满足长度要求。
 *
 * <p>下限 8 上限 72(BCrypt 有效输入长度)是本轮实现按常识定的策略,业务建模文档没有给出具体数值——interfaces
 * 层的 Bean Validation 已经在 DTO 上挡了一道同样的范围,这里是 application 层对同一条规则的第二道防线:
 * 万一将来有别的入口(批处理、内部工具)绕过 HTTP DTO 直接调用 {@code AuthApplicationService},密码长度这类
 * 安全相关的下限不应该只靠"调用方记得填对"。
 */
public class InvalidPasswordException extends BusinessException {

    private static final String CODE = "PASSWORD_INVALID";

    public InvalidPasswordException(String message) {
        super(CODE, message);
    }
}
