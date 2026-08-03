package com.landray.skillhub.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 一条评论(或回复)。业务建模文档 5.11。
 *
 * <p><b>只允许一层回复</b>:{@code parentId} 为 {@code null} 是顶级评论,非 {@code null} 时被指向者必须是顶级
 * 且属于同一个 skill —— 这条不变量由 application 层在写入前校验(数据库拦不住,没有外键)。因此
 * {@code replies} 只可能挂在顶级评论上,回复自己的 {@code replies} 恒为空列表。
 *
 * <p><b>{@code content} 在已删除的评论上是 {@code null}</b>,不是"该评论已删除"这句话。占位文案是展示决策,
 * 归前端;而让已删除的正文根本不出仓储,是为了让"不小心把它渲染出去"这件事在类型上就做不到 ——
 * 如果这里塞的是原文,那么任何一处忘了判 status 的代码都会把删掉的内容重新展示出来。
 *
 * <p>{@code author} 在作者账号被删时理论上可为 {@code null}(逻辑外键,没有数据库约束兜底)。当前没有删用户的
 * 功能(禁用不删行),但读侧不假设它一定在 —— 假设一个没有约束保证的东西,代价是某天一条 NPE 打在列表接口上。
 */
public record SkillComment(
        Long id,
        Long skillId,
        Long parentId,
        SkillOwner author,
        String content,
        CommentStatus status,
        Instant createdAt,
        List<SkillComment> replies) {

    public SkillComment {
        replies = replies == null ? List.of() : List.copyOf(replies);
    }

    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }

    /** 顶级评论。仅用于表达意图,避免调用方到处写 {@code parentId() == null} 这种需要二次翻译的判断。 */
    public boolean isTopLevel() {
        return parentId == null;
    }
}
