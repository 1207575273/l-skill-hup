package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** {@code source} 查询参数不属于受支持的来源枚举。 */
public class InvalidSkillSourceException extends BusinessException {

    private static final String CODE = "SKILL_SOURCE_INVALID";

    public InvalidSkillSourceException(String message) {
        super(CODE, message);
    }
}
