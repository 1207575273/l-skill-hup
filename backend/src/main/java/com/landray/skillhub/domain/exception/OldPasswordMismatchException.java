package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 改密码时旧密码校验不通过。
 *
 * <p>与 {@link InvalidCredentialException} 分开成两个错误码——虽然都是"密码校验失败",但一个发生在登录场景
 * （不该暴露账号是否存在),一个发生在已登录用户主动改密码场景(此时身份已确认,前端需要一个专门的错误码提示
 * "旧密码填错了",体验上不应复用登录失败的通用文案)。
 */
public class OldPasswordMismatchException extends BusinessException {

    private static final String CODE = "OLD_PASSWORD_INVALID";

    public OldPasswordMismatchException() {
        super(CODE, "原密码不正确");
    }
}
