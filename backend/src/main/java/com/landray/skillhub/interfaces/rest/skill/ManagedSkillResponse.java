package com.landray.skillhub.interfaces.rest.skill;

import java.time.Instant;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.Skill;

/**
 * {@code GET /skills/manage} 的行,给管理页用。
 *
 * <p><b>为什么不复用 {@link SkillSummaryResponse}。</b>那个 DTO 刻意不带 {@code status}——公开列表只返回
 * {@code PUBLISHED},带上一个恒定值纯属噪音。而管理页的核心就是区分已发布与已下架,{@code status} 是它的
 * 主信息。给公开 DTO 加一个只有管理页用得上的字段,等于让每个匿名请求都多背一个字段,并且让"这个字段什么时候
 * 有意义"变成需要看注释才知道的事。
 *
 * <p>字段刻意比 {@link SkillSummaryResponse} 少:管理页是一张操作表格,不渲染卡片,所以不需要摘要、标签、
 * 点赞收藏数。少返回的字段不会被人误用。
 */
public record ManagedSkillResponse(
        Long id,
        String name,
        String displayName,
        String status,
        String certification,
        long downloadCount,
        Instant updatedAt,
        Long latestVersionId) {

    public static ManagedSkillResponse from(Skill skill) {
        String certification = skill.latestVersion() == null
                ? Certification.NONE.name()
                : skill.latestVersion().certification().name();
        Long latestVersionId = skill.latestVersion() == null ? null : skill.latestVersion().id();
        return new ManagedSkillResponse(
                skill.id(),
                skill.name().value(),
                skill.displayName(),
                skill.status().name(),
                certification,
                skill.downloadCount(),
                skill.updatedAt(),
                latestVersionId);
    }
}
