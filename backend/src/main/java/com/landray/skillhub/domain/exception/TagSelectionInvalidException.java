package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 一次打标签操作违反了业务建模文档 5.7 的数量约束(CURATED 1~3、FREE 至多 10),或引用了不存在 / kind 不符的
 * CURATED 标签 id。**数据库表达不了这些约束**(4.7 节明写),必须在应用层同一事务内校验,这个异常就是那道校验的
 * 出口。
 */
public class TagSelectionInvalidException extends BusinessException {

    private static final String CODE = "TAG_SELECTION_INVALID";

    public TagSelectionInvalidException(String message) {
        super(CODE, message);
    }
}
