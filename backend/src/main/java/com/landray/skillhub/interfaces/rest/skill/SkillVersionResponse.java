package com.landray.skillhub.interfaces.rest.skill;

import java.time.Instant;

import com.landray.skillhub.domain.model.SkillVersion;

/** {@code GET /skills/{id}/versions} 的一个版本条目,不含正文(见 {@link SkillVersion} 类注释)。 */
public record SkillVersionResponse(
        Long id,
        String version,
        long sizeBytes,
        int fileCount,
        String certification,
        Long certifiedByUserId,
        Instant certifiedAt,
        String certifyNote,
        Long createdByUserId,
        Instant createdAt) {

    public static SkillVersionResponse from(SkillVersion version) {
        return new SkillVersionResponse(
                version.id(),
                version.version(),
                version.sizeBytes(),
                version.fileCount(),
                version.certification().name(),
                version.certifiedByUserId(),
                version.certifiedAt(),
                version.certifyNote(),
                version.createdByUserId(),
                version.createdAt());
    }
}
