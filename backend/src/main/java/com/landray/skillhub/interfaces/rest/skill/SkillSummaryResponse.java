package com.landray.skillhub.interfaces.rest.skill;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.Skill;

/**
 * {@code GET /skills} 列表项,字段覆盖业务建模文档 7.1 卡片所需的七个元素(图标由前端从 name/displayName
 * 派生,不需要服务端参与)。刻意不含 {@code description} / {@code dislikeCount} / {@code commentCount} /
 * {@code status}——7.1 定义的卡片没有用到它们,列表页也不该带上详情页才需要的重字段。
 */
public record SkillSummaryResponse(
        Long id,
        String name,
        String displayName,
        String cardExcerpt,
        String certification,
        long downloadCount,
        int likeCount,
        int favoriteCount,
        Instant updatedAt,
        SkillOwnerResponse owner,
        List<SkillTagResponse> tags) {

    public static SkillSummaryResponse from(Skill skill) {
        String certification = skill.latestVersion() == null
                ? Certification.NONE.name()
                : skill.latestVersion().certification().name();
        return new SkillSummaryResponse(
                skill.id(),
                skill.name().value(),
                skill.displayName(),
                skill.cardExcerpt(),
                certification,
                skill.downloadCount(),
                skill.likeCount(),
                skill.favoriteCount(),
                skill.updatedAt(),
                SkillOwnerResponse.from(skill.owner()),
                skill.tags().stream().map(SkillTagResponse::from).toList());
    }
}
