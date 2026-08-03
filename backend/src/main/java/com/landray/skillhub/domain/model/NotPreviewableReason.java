package com.landray.skillhub.domain.model;

/**
 * {@link SkillVersionFileContent} 判定"不能预览"时给出的原因,供前端区分展示文案(需求契约:
 * {@code GET /skills/{skillId}/versions/{versionId}/file})。
 */
public enum NotPreviewableReason {
    /** 超过预览大小上限(见 {@link SkillVersionFilePreviewer#MAX_PREVIEWABLE_BYTES})。 */
    TOO_LARGE,
    /** 内容含 NUL 字节(0x00),按内容探测判定为二进制文件,不是按扩展名白名单。 */
    BINARY
}
