package com.landray.skillhub.interfaces.rest.comment;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.interfaces.rest.skill.SkillOwnerResponse;

/**
 * 一条评论(或回复)的响应体。
 *
 * <p><b>{@code content} 对已删除的评论恒为 {@code null},而不是"该评论已删除"这句话。</b>占位文案是展示决策
 * (要不要显示、显示成什么、什么语言),归前端;服务端只如实告知"这条被删了、正文没有"。把文案写死在这里,
 * 等于让 API 承担了它不该承担的展示职责,而且将来做多语言时这句话会成为一个改不动的硬编码。
 *
 * <p>{@code replies} 只可能出现在顶级评论上(业务建模文档 5.11 只允许一层回复),回复自己的 {@code replies}
 * 恒为空数组 —— <b>是空数组不是 null</b>,前端不用为"没有回复"和"字段缺失"写两套判断。
 */
public record SkillCommentResponse(
        Long id,
        Long parentId,
        SkillOwnerResponse author,
        String content,
        boolean deleted,
        Instant createdAt,
        List<SkillCommentResponse> replies) {

    public static SkillCommentResponse from(SkillComment comment) {
        return new SkillCommentResponse(
                comment.id(),
                comment.parentId(),
                SkillOwnerResponse.from(comment.author()),
                comment.content(),
                comment.isDeleted(),
                comment.createdAt(),
                comment.replies().stream().map(SkillCommentResponse::from).toList());
    }
}
