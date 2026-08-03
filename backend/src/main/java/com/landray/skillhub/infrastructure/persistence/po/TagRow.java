package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;

import com.landray.skillhub.domain.model.TagKind;

/** {@code TagMapper.selectAll} 的一行,{@code skillCount} 是 GROUP BY 实时算出的可见 skill 数。 */
public record TagRow(
        Long id,
        String name,
        TagKind kind,
        String description,
        int sortOrder,
        Long createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        long skillCount) {}
