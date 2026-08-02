package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** skill 名称不合法。 */
public class InvalidSkillNameException extends BusinessException {

    private static final String CODE = "SKILL_NAME_INVALID";

    public InvalidSkillNameException(String message) {
        super(CODE, message);
    }
}
