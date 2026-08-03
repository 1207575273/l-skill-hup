package com.landray.skillhub.domain.model;

/**
 * {@code GET /skills/{skillId}/versions/{versionId}/file} 的领域层结果:某个具体文件是否可安全地当文本预览,
 * 及其解码后的正文。
 *
 * <p>与 {@link SkillVersionFile}(文件树清单的一个条目,只有 {@code path + sizeBytes})是姊妹但不同的概念——
 * 那个服务于"展开文件树",这个服务于"点开某个文件看内容",额外带正文与"为什么不能看"的原因。判定规则(阈值、
 * 二进制探测)由 {@link SkillVersionFilePreviewer} 决定,本 record 只是承载判定结果的不可变值对象,自身只保证
 * "字段组合内部自洽"(可预览时有正文没有原因,不可预览时有原因没有正文)。
 */
public record SkillVersionFileContent(
        String path, long sizeBytes, boolean previewable, String content, NotPreviewableReason notPreviewableReason) {

    public SkillVersionFileContent {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes 不能为负数");
        }
        if (previewable && notPreviewableReason != null) {
            throw new IllegalArgumentException("previewable 为 true 时 notPreviewableReason 必须为空");
        }
        if (previewable && content == null) {
            throw new IllegalArgumentException("previewable 为 true 时 content 不能为空");
        }
        if (!previewable && content != null) {
            throw new IllegalArgumentException("previewable 为 false 时 content 必须为空");
        }
        if (!previewable && notPreviewableReason == null) {
            throw new IllegalArgumentException("previewable 为 false 时必须给出 notPreviewableReason");
        }
    }

    public static SkillVersionFileContent previewable(String path, long sizeBytes, String content) {
        return new SkillVersionFileContent(path, sizeBytes, true, content, null);
    }

    public static SkillVersionFileContent notPreviewable(String path, long sizeBytes, NotPreviewableReason reason) {
        return new SkillVersionFileContent(path, sizeBytes, false, null, reason);
    }
}
