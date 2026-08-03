package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/** 评论正文不合法(空,或超过 2000 字符上限,业务建模文档 5.11)。 */
public class CommentContentInvalidException extends BusinessException {

    private static final String CODE = "COMMENT_CONTENT_INVALID";

    public CommentContentInvalidException(String message) {
        super(CODE, message);
    }
}
