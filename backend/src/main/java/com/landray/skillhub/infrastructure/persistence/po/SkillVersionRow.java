package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;

import com.landray.skillhub.domain.model.Certification;

/**
 * {@code SkillMapper.selectVersionsBySkillId} 的一行,用于 {@code GET /skills/{id}/versions}。
 *
 * <p>不含 {@code allowedTools} / {@code skillMdContent} / {@code readmeContent} / {@code changelog} 四个重字段
 * ——这是"查看某个历史版本列表"场景,不是"查看某个版本正文",与 {@link SkillListRow} 排除重字段是同一个理由。
 */
public record SkillVersionRow(
        Long id,
        String version,
        long sizeBytes,
        int fileCount,
        Certification certification,
        Long certifiedByUserId,
        Instant certifiedAt,
        String certifyNote,
        Long createdByUserId,
        Instant createdAt) {}
