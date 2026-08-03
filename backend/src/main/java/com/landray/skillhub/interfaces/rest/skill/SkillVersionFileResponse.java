package com.landray.skillhub.interfaces.rest.skill;

import com.landray.skillhub.domain.model.SkillVersionFile;

/**
 * {@code GET /skills/{id}} 详情里 {@code latestVersion.files} 数组的一个元素,供前端把版本展开成文件树。
 *
 * <p>只在这一个响应类型里出现——{@code GET /skills/{id}/versions} 的 {@link SkillVersionResponse} 刻意不带
 * 这个字段(契约已定死,列表场景不需要,省响应体)。
 */
public record SkillVersionFileResponse(String path, long sizeBytes) {

    public static SkillVersionFileResponse from(SkillVersionFile file) {
        return new SkillVersionFileResponse(file.path(), file.sizeBytes());
    }
}
