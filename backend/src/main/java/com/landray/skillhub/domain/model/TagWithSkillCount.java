package com.landray.skillhub.domain.model;

/**
 * {@code GET /tags} 的查询结果:一个词条 + 它当前挂了多少个可见(PUBLISHED)skill。
 *
 * <p>skill 数不是 {@link Tag} 的固有属性,而是特定读场景下的聚合结果(业务建模文档 6.2:"附带各词条下的 skill
 * 数(GROUP BY 实时算)"),因此不并入 {@link Tag} 本体,单独用一个轻量包装承载——{@code tag} 表本身不存冗余的
 * {@code skill_count} 列(理由见文档通用约定第 8 条:标签几十条,GROUP BY 是毫秒级)。
 */
public record TagWithSkillCount(Tag tag, long skillCount) {}
