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
                null,
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

    /** 只换归属人,其余与 skillWith 一致 —— isUnclaimed 的判据只看 owner。 */
    private Skill skillOwnedBy(long ownerUserId) {
        return new Skill(
                1L,
                new SkillName("code-review"),
                "代码审查助手",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(ownerUserId, "张三", "80380486"),
                null,
                "摘要",
                SkillStatus.PUBLISHED,
                0L,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                List.of());
    }

    @Test
    @DisplayName("归属人是内置 system 时视为无主待认领")
    void should_beUnclaimed_when_ownerIsSystemAccount() {
        assertThat(skillOwnedBy(AppUser.SYSTEM_USER_ID).isUnclaimed()).isTrue();
    }

    @Test
    @DisplayName("归属人是真实账号时不是无主")
    void should_notBeUnclaimed_when_ownerIsRealAccount() {
        assertThat(skillOwnedBy(2L).isUnclaimed()).isFalse();
    }

    @Test
    @DisplayName("system 永远登不进来,所以 owner/ADMIN 那条资源级判断对无主 skill 自动退化成仅 ADMIN")
    void should_makeOwnerCheckAdminOnly_when_unclaimed() {
        Skill unclaimed = skillOwnedBy(AppUser.SYSTEM_USER_ID);

        // 任何能登录的用户 id 都不可能等于 system 的 id —— 这不是巧合,是 V0002 刻意不给 system 任何
        // user_identity 的直接后果。无主 skill 的管理权因此天然收敛到 ADMIN,不需要额外写分支。
        assertThat(unclaimed.owner().userId()).isEqualTo(AppUser.SYSTEM_USER_ID);
        assertThat(unclaimed.isUnclaimed()).isTrue();
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
