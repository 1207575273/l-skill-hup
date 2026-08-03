package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;

import com.landray.skillhub.domain.model.CommentStatus;

/**
 * {@code skill_comment} join {@code app_user} 的扁平结果行。
 *
 * <p>与 {@code SkillComment} 领域模型的差别有两处,这正是加 PO 的理由(backend/CLAUDE.md 第二节):
 * 作者是三个扁平列而不是嵌套的 {@code SkillOwner};回复是另一批行而不是嵌套的 {@code replies} 列表 ——
 * 由 {@code SkillCommentRepositoryImpl} 按 {@code parentId} 拼装。
 *
 * <p>{@code content} 对已删除的行由 <b>SQL 直接置空</b>,不是取出来之后再抹掉。让已删除的正文根本不进入
 * 这个进程,比"取出来后记得别用"可靠 —— 后者依赖每一个调用方都记得判断。
 */
public record SkillCommentRow(
        Long id,
        Long skillId,
        Long parentId,
        Long authorUserId,
        String authorDisplayName,
        String authorEmployeeNo,
        String content,
        CommentStatus status,
        Instant createdAt) {}
