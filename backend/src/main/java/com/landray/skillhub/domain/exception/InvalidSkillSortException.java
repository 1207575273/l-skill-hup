package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** {@code sort} 查询参数不属于受支持的排序方式。 */
public class InvalidSkillSortException extends BusinessException {

    private static final String CODE = "SKILL_SORT_INVALID";

    public InvalidSkillSortException(String message) {
        super(CODE, message);
    }
}
