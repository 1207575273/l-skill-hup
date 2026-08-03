package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 回复目标不合法:被指向的评论不存在、不属于这个 skill、已被删除,或者它自己就是一条回复。
 *
 * <p>业务建模文档 5.11 只允许<b>一层</b>回复。四种情况合成一个错误码而不是拆开,是刻意的:对调用方来说
 * 下一步动作完全一样(重新选一个可回复的目标),而分开返回等于把"这个 id 到底存不存在"告诉了一个
 * 本来就不该知道的人 —— 与 skill 版本文件那处"版本不存在与版本属于另一个 skill 统一 404"同一条判断。
 */
public class CommentReplyTargetInvalidException extends BusinessException {

    private static final String CODE = "COMMENT_REPLY_TARGET_INVALID";

    public CommentReplyTargetInvalidException() {
        super(CODE, "只能回复该 skill 下未删除的顶级评论");
    }
}
