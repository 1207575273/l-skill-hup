package com.landray.skillhub.domain.model;

import java.time.Instant;

/**
 * 一个词条。分类与标签是同一个东西,靠 {@link #kind()} 区分职责(业务建模文档 4.6)。
 *
 * <p>本读侧任务里既用作 {@code skill.tags} 的元素(标签行展示),也用作 {@link TagWithSkillCount} 的载荷
 * ({@code GET /tags}),两处都能取到完整字段,不存在"部分字段为 null 的降级视图"。
 */
public record Tag(
        Long id,
        String name,
        TagKind kind,
        String description,
        int sortOrder,
        Long createdByUserId,
        Instant createdAt,
        Instant updatedAt) {}
