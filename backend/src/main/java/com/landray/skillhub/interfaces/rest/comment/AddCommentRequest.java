package com.landray.skillhub.interfaces.rest.comment;

/**
 * 发表评论 / 回复的请求体。
 *
 * <p><b>刻意不加 Bean Validation 注解。</b>正文的非空与长度是业务规则,已经由
 * {@link com.landray.skillhub.domain.model.CommentContent} 值对象守住 —— 在这里再写一遍 {@code @NotBlank}
 * 就有了两份会各自演化的规则,而它们不一致时(比如某天上限从 2000 改成 4000 只改了一处)
 * 表现是"前端说能提交、后端说不行",排查时两边都觉得自己是对的。
 *
 * <p>{@code parentId} 为空表示发顶级评论,非空表示回复。
 */
public record AddCommentRequest(Long parentId, String content) {}
