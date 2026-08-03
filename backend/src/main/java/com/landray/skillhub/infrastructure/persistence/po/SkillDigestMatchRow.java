package com.landray.skillhub.infrastructure.persistence.po;

/**
 * {@code SkillMapper.selectVersionByContentDigest} 的一行,只承载 precheck "完全相同的包已存在"判定需要的两个
 * 字段(业务建模文档 6.2 precheck 响应示例里的 {@code identical: {skillId, version}})。
 */
public record SkillDigestMatchRow(Long id, Long skillId, String version) {}
