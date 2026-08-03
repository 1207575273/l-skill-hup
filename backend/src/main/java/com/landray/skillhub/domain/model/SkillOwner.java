package com.landray.skillhub.domain.model;

/**
 * skill 归属人的展示信息。
 *
 * <p>业务建模文档 3.3 / 7.1:工号必须跟在展示名后面(展示为 {@code @张三(80380486)})——同名同姓在公司规模上是
 * 必然事件,只显示展示名等于没有显示归属。{@code employeeNo} 在 LOCAL 建号未绑钉钉时可为 {@code null}。
 */
public record SkillOwner(Long userId, String displayName, String employeeNo) {}
