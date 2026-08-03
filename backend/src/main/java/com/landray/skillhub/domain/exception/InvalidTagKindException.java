package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** {@code kind} 查询参数不属于受支持的标签类型。 */
public class InvalidTagKindException extends BusinessException {

    private static final String CODE = "TAG_KIND_INVALID";

    public InvalidTagKindException(String message) {
        super(CODE, message);
    }
}
