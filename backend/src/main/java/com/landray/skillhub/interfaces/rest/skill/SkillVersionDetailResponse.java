package com.landray.skillhub.interfaces.rest.skill;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.SkillVersionFile;

/**
 * {@code GET /skills/{id}} 详情里的最新版本,含正文。业务建模文档 5.6:详情页 {@code readmeContent}
 * 非空则优先渲染它,否则渲染 {@code skillMdContent}——两个字段原样回传,由前端按此规则二选一渲染,不在服务端
 * 预先合并成一个字段。
 *
 * <p>{@code files} 是详情页文件树:现读制品 zip 得到,不落库,按 path 字典序,空清单是 {@code []} 不是
 * {@code null}(见 {@link SkillVersionFile} 类注释)。{@link #from(SkillVersion)} 单参重载给
 * 改元数据/上下架/发布这类写侧回显复用同一个 DTO 但不需要现读制品的场景,默认给空列表。
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
        Instant createdAt,
        List<SkillVersionFileResponse> files) {

    public static SkillVersionDetailResponse from(SkillVersion version) {
        return from(version, List.of());
    }

    public static SkillVersionDetailResponse from(SkillVersion version, List<SkillVersionFile> files) {
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
                version.createdAt(),
                files.stream().map(SkillVersionFileResponse::from).toList());
    }
}
