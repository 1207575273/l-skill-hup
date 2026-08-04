package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 词条名不满足业务建模文档 5.7 的归一化规则:空白、或归一化后超过 16 字符。
 *
 * <p>与 {@link TagSelectionInvalidException} 分开是因为两者的责任方不同:那个说的是"你这次选的这组标签数量或
 * 类型不对",这个说的是"这个名字本身就不合法"。同一个 400 里给不同的 code,前端才能把错误提示挂到正确的
 * 输入框上。
 */
public class TagNameInvalidException extends BusinessException {

    private static final String CODE = "TAG_NAME_INVALID";

    public TagNameInvalidException(String message) {
        super(CODE, message);
    }
}
