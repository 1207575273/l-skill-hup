package com.landray.skillhub.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * skill 的一个已发布版本(业务建模文档 4.9)。
 *
 * <p>{@code artifactKey} / {@code contentDigest} / {@code sha256} 三个字段服务于制品存储寻址、去重检测、
 * 安装脚本内嵌校验(上传管线任务补齐,原读侧实现的类注释里预留了这句话:"写侧落地时如果需要这些字段,直接在这个
 * record 里补齐即可,不需要新起一个类型")。读侧查询(列表 / 版本列表)不 SELECT 这三列,对应字段就是
 * {@code null}——与 {@code skillMdContent} 在列表场景同样是 {@code null},是同一种"按需选择性查询"的模式,
 * 见 {@link Skill} 类注释关于 latestVersion 的说明。
 */
public record SkillVersion(
        Long id,
        Long skillId,
        String version,
        String artifactKey,
        String contentDigest,
        String sha256,
        long sizeBytes,
        int fileCount,
        List<String> allowedTools,
        String skillMdContent,
        String readmeContent,
        String changelog,
        Certification certification,
        Long certifiedByUserId,
        Instant certifiedAt,
        String certifyNote,
        Long createdByUserId,
        Instant createdAt) {

    public boolean isCertified() {
        return certification == Certification.CERTIFIED;
    }

    public boolean isRevoked() {
        return certification == Certification.REVOKED;
    }
}
