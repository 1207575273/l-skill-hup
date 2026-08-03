package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillTest {

    private Skill skillWith(SkillStatus status, SkillVersion latestVersion) {
        return new Skill(
                1L,
                new SkillName("code-review"),
                "代码审查助手",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(2L, "张三", "80380486"),
                "摘要",
                status,
                0L,
                0,
                0,
                0,
                0,
                null,
                null,
                latestVersion,
                List.of());
    }

    private SkillVersion versionWith(Certification certification) {
        return new SkillVersion(
                1L, 1L, "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), "0".repeat(64), 100L, 1, List.of(),
                "# skill", null, null, certification, null, null, null, 1L, null);
    }

    @Test
    @DisplayName("status 为 PUBLISHED 时应出现在列表里")
    void should_beVisibleInListing_when_statusIsPublished() {
        Skill skill = skillWith(SkillStatus.PUBLISHED, null);

        assertThat(skill.isVisibleInListing()).isTrue();
    }

    @Test
    @DisplayName("status 为 UNLISTED 时不应出现在列表里")
    void should_notBeVisibleInListing_when_statusIsUnlisted() {
        Skill skill = skillWith(SkillStatus.UNLISTED, null);

        assertThat(skill.isVisibleInListing()).isFalse();
    }

    @Test
    @DisplayName("最新版本已认证时 skill 视为已认证")
    void should_beCertified_when_latestVersionIsCertified() {
        Skill skill = skillWith(SkillStatus.PUBLISHED, versionWith(Certification.CERTIFIED));

        assertThat(skill.isCertified()).isTrue();
    }

    @Test
    @DisplayName("没有任何版本时视为未认证,不抛异常")
    void should_notBeCertified_when_noLatestVersion() {
        Skill skill = skillWith(SkillStatus.PUBLISHED, null);

        assertThat(skill.isCertified()).isFalse();
    }
}
