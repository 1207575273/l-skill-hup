package com.landray.skillhub.infrastructure.persistence.po;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.SkillStatus;

/**
 * {@code SkillArtifactMapper} 的扁平查询结果行,join {@code skill} 与 {@code skill_version}。
 *
 * <p>{@code skillStatus} / {@code certification} 直接声明成领域枚举类型:两列的取值(如 {@code PUBLISHED} /
 * {@code CERTIFIED})与 Java 枚举常量名精确一致,MyBatis 默认的 {@code EnumTypeHandler} 可以直接工作,不需要
 * 像 {@link InstallBundleRow} 那样额外转换——两处对比正说明"是否需要 PO 转换"取决于数据库值域与枚举命名是否
 * 一致,不是一刀切。
 */
public record SkillArtifactRow(
        Long skillVersionId,
        Long skillId,
        String skillName,
        String version,
        String artifactKey,
        String sha256,
        long sizeBytes,
        SkillStatus skillStatus,
        Certification certification,
        String certifyNote) {}
