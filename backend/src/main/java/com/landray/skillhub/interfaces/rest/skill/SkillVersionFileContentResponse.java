package com.landray.skillhub.interfaces.rest.skill;

import com.landray.skillhub.domain.model.SkillVersionFileContent;

/**
 * {@code GET /skills/{skillId}/versions/{versionId}/file} 响应,供详情页文件树点开某个节点后展示内容。
 *
 * <p>{@code notPreviewableReason} 是 {@code previewable=false} 时的原因("BINARY" 或 "TOO_LARGE"),
 * {@code previewable=true} 时为 {@code null}——与 {@code content} 的可空性互斥,由领域层
 * {@link SkillVersionFileContent} 的构造器保证内部自洽,本响应类只是原样透传。
 */
public record SkillVersionFileContentResponse(
        String path, long sizeBytes, boolean previewable, String content, String notPreviewableReason) {

    public static SkillVersionFileContentResponse from(SkillVersionFileContent result) {
        return new SkillVersionFileContentResponse(
                result.path(),
                result.sizeBytes(),
                result.previewable(),
                result.content(),
                result.notPreviewableReason() == null
                        ? null
                        : result.notPreviewableReason().name());
    }
}
