package com.landray.skillhub.interfaces.rest.skill;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillVersionFile;

/**
 * {@code GET /skills/{id}} 详情响应。刻意不含"当前用户的表态与收藏"字段——那是 P2 收藏/点赞点踩上线后才有意义
 * 的内容,本任务范围只做匿名可用的读侧四个接口。
 */
public record SkillDetailResponse(
        Long id,
        String name,
        String displayName,
        String description,
        String source,
        String status,
        String cardExcerpt,
        long downloadCount,
        int likeCount,
        int dislikeCount,
        int favoriteCount,
        int commentCount,
        Instant createdAt,
        Instant updatedAt,
        SkillOwnerResponse owner,
        DeclaredOwnerResponse declaredOwner,
        boolean unclaimed,
        List<SkillTagResponse> tags,
        SkillVersionDetailResponse latestVersion) {

    public static SkillDetailResponse from(Skill skill) {
        return from(skill, List.of());
    }

    public static SkillDetailResponse from(Skill skill, List<SkillVersionFile> latestVersionFiles) {
        return new SkillDetailResponse(
                skill.id(),
                skill.name().value(),
                skill.displayName(),
                skill.description(),
                skill.source().name(),
                skill.status().name(),
                skill.cardExcerpt(),
                skill.downloadCount(),
                skill.likeCount(),
                skill.dislikeCount(),
                skill.favoriteCount(),
                skill.commentCount(),
                skill.createdAt(),
                skill.updatedAt(),
                SkillOwnerResponse.from(skill.owner()),
                DeclaredOwnerResponse.from(skill.declaredOwner()),
                skill.isUnclaimed(),
                skill.tags().stream().map(SkillTagResponse::from).toList(),
                SkillVersionDetailResponse.from(skill.latestVersion(), latestVersionFiles));
    }
}
