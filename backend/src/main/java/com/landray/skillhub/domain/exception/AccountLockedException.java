package com.landray.skillhub.domain.exception;

import java.time.Instant;
import java.util.Map;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 该登录方式因连续登录失败被锁定,锁定期内即使密码正确也拒绝(业务建模文档 3.2)。
 */
public class AccountLockedException extends BusinessException {

    private static final String CODE = "ACCOUNT_LOCKED";
    private static final int HTTP_STATUS_FORBIDDEN = 403;

    public AccountLockedException(Instant lockedUntil) {
        super(
                CODE,
                "登录失败次数过多,该账号已被临时锁定,请于 " + lockedUntil + " 之后重试",
                HTTP_STATUS_FORBIDDEN,
                Map.of("lockedUntil", lockedUntil.toString()));
    }
}
