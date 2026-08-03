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
        List<SkillTagResponse> tags,
        /**
         * 最新版本的 id,没有任何版本时为 {@code null}。
         *
         * <p>列表页"加入安装单"需要 {@code versionId},而这个值本来就已经在手上 ——
         * {@code certification} 就是从 {@code skill.latestVersion()} 取的,多返回一个 id 零查询成本。
         * 不给的话前端只能在每次点击时再发一次请求去查版本列表,平白多一个往返。
         */
        Long latestVersionId) {

    public static SkillSummaryResponse from(Skill skill) {
        String certification = skill.latestVersion() == null
                ? Certification.NONE.name()
                : skill.latestVersion().certification().name();
        Long latestVersionId = skill.latestVersion() == null ? null : skill.latestVersion().id();
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
                skill.tags().stream().map(SkillTagResponse::from).toList(),
                latestVersionId);
    }
}
