package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 登录名不存在或密码错误。
 *
 * <p>刻意用同一个错误码 + 固定文案覆盖"登录名不存在"与"密码错误"两种场景,不区分对外暴露——区分会让攻击者可以用
 * "报错文案不同"当作账号是否存在的探测手段(user enumeration),内网工具同样不该有这个口子。
 */
public class InvalidCredentialException extends BusinessException {

    private static final String CODE = "LOGIN_FAILED";
    private static final int HTTP_STATUS_UNAUTHORIZED = 401;
    private static final String MESSAGE = "登录名或密码错误";

    public InvalidCredentialException() {
        super(CODE, MESSAGE, HTTP_STATUS_UNAUTHORIZED);
    }
}
