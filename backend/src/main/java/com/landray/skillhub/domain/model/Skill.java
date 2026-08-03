package com.landray.skillhub.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 一个 skill 的稳定身份与聚合统计(业务建模文档 4.8)。
 *
 * <p>{@link #latestVersion()} 在列表场景与详情场景填充深度不同:列表只带 {@code id / version /
 * certification} 三个字段(卡片只需要认证徽章),详情带全部字段(含正文)。这不是两个类型,是同一个
 * {@link SkillVersion} 在不同读场景下被有意选择性地查询——哪些字段进 SELECT 由
 * {@code infrastructure.persistence} 的两条不同 SQL 决定,domain 层不感知这个差异。
 */
public record Skill(
        Long id,
        SkillName name,
        String displayName,
        String description,
        SkillSource source,
        SkillOwner owner,
        String cardExcerpt,
        SkillStatus status,
        long downloadCount,
        int likeCount,
        int dislikeCount,
        int favoriteCount,
        int commentCount,
        Instant createdAt,
        Instant updatedAt,
        SkillVersion latestVersion,
        List<Tag> tags) {

    /** 是否出现在列表 / 搜索里——业务建模文档 4.8:下架(UNLISTED)后不出现在列表。 */
    public boolean isVisibleInListing() {
        return status == SkillStatus.PUBLISHED;
    }

    /** 最新版本是否已获平台认证。没有任何版本时视为未认证。 */
    public boolean isCertified() {
        return latestVersion != null && latestVersion.isCertified();
    }
}
