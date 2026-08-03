package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** {@code version} 不满足 SemVer 格式(业务建模文档 5.4 第 2 条)。 */
public class InvalidSkillVersionException extends BusinessException {

    private static final String CODE = "SKILL_VERSION_INVALID";

    public InvalidSkillVersionException(String message) {
        super(CODE, message);
    }
}
