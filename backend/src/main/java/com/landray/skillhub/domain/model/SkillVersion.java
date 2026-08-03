package com.landray.skillhub.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * skill 的一个已发布版本(业务建模文档 4.9)。
 *
 * <p><b>本次读侧实现刻意不携带 {@code artifactKey} / {@code contentDigest} / {@code sha256}
 * 三个字段</b>——它们分别服务于制品存储寻址、去重检测、安装脚本内嵌校验,这三件事都不在本任务范围内(P0 读侧只做
 * 列表 / 详情 / 版本列表 / 标签四个只读接口)。写侧(上传、发版、安装单渲染)落地时如果需要这些字段, 直接在这个
 * record 里补齐即可,不需要新起一个类型——遵循 YAGNI,不为还没有调用方的字段预留占位。
 */
public record SkillVersion(
        Long id,
        Long skillId,
        String version,
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
