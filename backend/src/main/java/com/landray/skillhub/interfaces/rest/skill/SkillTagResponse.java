package com.landray.skillhub.interfaces.rest.skill;

import com.landray.skillhub.domain.model.Tag;

/**
 * skill 详情/卡片上的标签行元素。业务建模文档 7.2:CURATED 不带 {@code #} 排最前,FREE 带 {@code #} 排其后,
 * 前端按 {@code kind} 决定是否加 {@code #} 前缀,本响应体只回传原始 {@code kind},不在服务端拼前缀。
 */
public record SkillTagResponse(Long id, String name, String kind, String description, int sortOrder) {

    public static SkillTagResponse from(Tag tag) {
        return new SkillTagResponse(tag.id(), tag.name(), tag.kind().name(), tag.description(), tag.sortOrder());
    }
}
