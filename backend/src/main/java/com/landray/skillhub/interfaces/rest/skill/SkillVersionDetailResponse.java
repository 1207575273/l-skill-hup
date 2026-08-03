package com.landray.skillhub.interfaces.rest.skill;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.model.SkillVersion;

/**
 * {@code GET /skills/{id}} 详情里的最新版本,含正文。业务建模文档 5.6:详情页 {@code readmeContent}
 * 非空则优先渲染它,否则渲染 {@code skillMdContent}——两个字段原样回传,由前端按此规则二选一渲染,不在服务端
 * 预先合并成一个字段。
 */
public record SkillVersionDetailResponse(
        Long id,
        String version,
        long sizeBytes,
        int fileCount,
        List<String> allowedTools,
        String skillMdContent,
        String readmeContent,
        String changelog,
        String certification,
        Long certifiedByUserId,
        Instant certifiedAt,
        String certifyNote,
        Long createdByUserId,
        Instant createdAt) {

    public static SkillVersionDetailResponse from(SkillVersion version) {
        if (version == null) {
            return null;
        }
        return new SkillVersionDetailResponse(
                version.id(),
                version.version(),
                version.sizeBytes(),
                version.fileCount(),
                version.allowedTools(),
                version.skillMdContent(),
                version.readmeContent(),
                version.changelog(),
                version.certification().name(),
                version.certifiedByUserId(),
                version.certifiedAt(),
                version.certifyNote(),
                version.createdByUserId(),
                version.createdAt());
    }
}
