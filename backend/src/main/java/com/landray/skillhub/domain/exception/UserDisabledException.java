package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** 账号已被管理员禁用,拒绝登录(业务建模文档 5.15:禁用用户撤销其全部会话)。 */
public class UserDisabledException extends BusinessException {

    private static final String CODE = "USER_DISABLED";
    private static final int HTTP_STATUS_FORBIDDEN = 403;

    public UserDisabledException() {
        super(CODE, "该账号已被禁用", HTTP_STATUS_FORBIDDEN);
    }
}
