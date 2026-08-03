package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;

import com.landray.skillhub.domain.model.TagKind;

/**
 * {@code SkillMapper.selectTagsBySkillIds} 的一行:一个 skill 与它挂的一个 tag 的关联,tag 全部字段都在。
 *
 * <p>批量按一组 {@code skillId} 取标签,由 {@code SkillRepositoryImpl} 在 Java 里按 {@code skillId} 分组后
 * 分别挂到对应的 {@code Skill.tags}——这是刻意避免在分页主查询里直接 join {@code skill_tag}/{@code tag}
 * 展开成多行(会打乱 LIMIT/OFFSET 分页的语义),用两次查询 + 内存分组换取分页正确性,详见
 * {@code SkillRepositoryImpl} 类注释。
 */
public record SkillTagRow(
        Long skillId,
        Long tagId,
        String tagName,
        TagKind tagKind,
        String tagDescription,
        int tagSortOrder,
        Long tagCreatedByUserId,
        Instant tagCreatedAt,
        Instant tagUpdatedAt) {}
