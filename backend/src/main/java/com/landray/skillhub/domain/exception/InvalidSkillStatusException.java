package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** {@code status} 查询参数不属于受支持的状态枚举。 */
public class InvalidSkillStatusException extends BusinessException {

    private static final String CODE = "SKILL_STATUS_INVALID";

    public InvalidSkillStatusException(String message) {
        super(CODE, message);
    }
}
