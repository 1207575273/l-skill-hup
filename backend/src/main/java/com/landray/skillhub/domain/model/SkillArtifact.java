package com.landray.skillhub.domain.model;

/**
 * 渲染安装脚本 / 直接下载制品时需要的窄投影,join {@code skill} 与 {@code skill_version} 得到。
 *
 * <p><b>刻意不复用 {@link SkillVersion}</b>——那个类型不携带 {@code artifactKey} / {@code sha256}(见其类注释,
 * 上传管线落地时才会补齐),而安装链路恰恰只需要这几个字段外加 {@code skill} 侧的 {@code name} / {@code status}。
 * 与其等一个仍在开发中的类型改形状,不如现在就定义一个专门服务于"下载与安装脚本渲染"这一件事的窄记录,字段随需
 * 而定(YAGNI),不为它还没有的用途预留占位。
 */
public record SkillArtifact(
        Long skillVersionId,
        Long skillId,
        String skillName,
        String version,
        String artifactKey,
        String sha256,
        long sizeBytes,
        SkillStatus skillStatus,
        Certification certification,
        String certifyNote) {

    public boolean isRevoked() {
        return certification == Certification.REVOKED;
    }

    public boolean isUnlisted() {
        return skillStatus == SkillStatus.UNLISTED;
    }
}
