package com.landray.skillhub.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.InvalidSkillNameException;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.ClientCode;
import com.landray.skillhub.domain.model.InstallBundle;
import com.landray.skillhub.domain.model.InstallItem;
import com.landray.skillhub.domain.model.InstallScope;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.model.SkillStatus;

/**
 * 覆盖根 CLAUDE.md 第七节安全底线在安装脚本里的落地:sha256 内嵌校验、只写既定目录、覆盖前备份、
 * REVOKED 默认中止 + {@code -Force}/{@code --force} 才继续、{@code .sh} 全文不出现 {@code \r}。
 */
class InstallScriptRendererTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plus(Duration.ofDays(30));
    private static final String PUBLIC_BASE_URL = "http://localhost:18080/skillhub";
    private static final String SHA256_UPPER = "AABBCCDD".repeat(8);

    private InstallBundle bundle(ClientCode client, InstallScope scope) {
        return new InstallBundle(
                1L, "sample-token-value", List.of(new InstallItem(1L, 10L)), client, scope, null, CREATED_AT,
                EXPIRES_AT);
    }

    private SkillArtifact artifact(SkillStatus status, Certification certification, String certifyNote) {
        return new SkillArtifact(
                10L, 1L, "code-review", "1.2.0", "artifacts/1/1.2.0.zip", SHA256_UPPER, 2048L, status,
                certification, certifyNote);
    }

    // ---------- PowerShell ----------

    @Test
    void should_embedLowercaseSha256AndDownloadUrl_when_renderingPowerShell() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains(SHA256_UPPER.toLowerCase(java.util.Locale.ROOT));
        assertThat(script).contains(PUBLIC_BASE_URL + "/api/v1/skills/1/versions/1.2.0/download");
        assertThat(script).contains("-Sha256");
        assertThat(script).contains("$actualHash -ne $Sha256");
    }

    @Test
    void should_useHomeDirectory_when_scopeIsGlobal() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains("Join-Path $HOME '.claude/skills'");
    }

    @Test
    void should_useCurrentWorkingDirectory_when_scopeIsProject() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CODEX, InstallScope.PROJECT),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains("Join-Path (Get-Location).Path '.codex/skills'");
    }

    @Test
    void should_includeBackupLogicBeforeOverwrite_when_renderingPowerShell() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains(".bak.");
        assertThat(script).contains("Move-Item -Path $targetDir -Destination $backupDir -Force");
    }

    @Test
    void should_requireForceFlagAndPrintNote_when_anyArtifactIsRevoked() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "存在安全隐患")), PUBLIC_BASE_URL);

        assertThat(script).contains("已被撤销");
        assertThat(script).contains("存在安全隐患");
        assertThat(script).contains("-Force");
        assertThat(script).contains("exit 1");
    }

    @Test
    void should_notMentionForceOrRevoked_when_noArtifactIsRevoked() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.CERTIFIED, null)), PUBLIC_BASE_URL);

        assertThat(script).doesNotContain("已被撤销");
        assertThat(script).doesNotContain("HasRevoked");
    }

    @Test
    void should_printNoticeButNotAbort_when_skillIsUnlisted() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.UNLISTED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains("已下架");
        assertThat(script).contains("Install-OneSkill -Name 'code-review'");
        // "exit 1" 本身在 sha256 校验失败分支里恒定存在(与是否 UNLISTED 无关),这里断言的是
        // REVOKED 专属的中止分支没有被生成——UNLISTED 不应该触发那一整段。
        assertThat(script).doesNotContain("HasRevoked");
        assertThat(script).doesNotContain("已被撤销");
    }

    @Test
    void should_escapeSingleQuoteInCertifyNote_when_renderingPowerShell() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "it's unsafe")), PUBLIC_BASE_URL);

        assertThat(script).contains("it''s unsafe");
    }

    @Test
    void should_stripCarriageReturnAndCollapseNewlines_when_certifyNoteContainsThem() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "line1\r\nline2\r\n")),
                PUBLIC_BASE_URL);

        assertThat(script).doesNotContain("\r");
        assertThat(script).contains("line1 line2");
    }

    @Test
    void should_throw_when_powerShellArtifactListIsEmpty() {
        assertThatThrownBy(() -> InstallScriptRenderer.renderPowerShell(
                        bundle(ClientCode.CLAUDE, InstallScope.GLOBAL), List.of(), PUBLIC_BASE_URL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_throw_when_versionContainsUnsafeCharacters() {
        SkillArtifact unsafeVersion = new SkillArtifact(
                10L, 1L, "code-review", "1.0.0; rm -rf /", "artifacts/1/x.zip", SHA256_UPPER, 1024L,
                SkillStatus.PUBLISHED, Certification.NONE, null);

        assertThatThrownBy(() -> InstallScriptRenderer.renderPowerShell(
                        bundle(ClientCode.CLAUDE, InstallScope.GLOBAL), List.of(unsafeVersion), PUBLIC_BASE_URL))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_throw_when_skillNameIsNotKebabCase() {
        SkillArtifact unsafeName = new SkillArtifact(
                10L, 1L, "../evil", "1.0.0", "artifacts/1/x.zip", SHA256_UPPER, 1024L, SkillStatus.PUBLISHED,
                Certification.NONE, null);

        assertThatThrownBy(() -> InstallScriptRenderer.renderPowerShell(
                        bundle(ClientCode.CLAUDE, InstallScope.GLOBAL), List.of(unsafeName), PUBLIC_BASE_URL))
                .isInstanceOf(InvalidSkillNameException.class);
    }

    @Test
    void should_renderOneInstallInvocationPerArtifact_when_multipleArtifactsProvided() {
        SkillArtifact second = new SkillArtifact(
                20L, 2L, "another-skill", "2.0.0", "artifacts/2/2.0.0.zip", "1".repeat(64), 4096L,
                SkillStatus.PUBLISHED, Certification.NONE, null);

        String script = InstallScriptRenderer.renderPowerShell(
                new InstallBundle(
                        1L, "sample-token-value", List.of(new InstallItem(1L, 10L), new InstallItem(2L, 20L)),
                        ClientCode.CLAUDE, InstallScope.GLOBAL, null, CREATED_AT, EXPIRES_AT),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null), second), PUBLIC_BASE_URL);

        assertThat(script).contains("Install-OneSkill -Name 'code-review'");
        assertThat(script).contains("Install-OneSkill -Name 'another-skill'");
    }

    // ---------- bash ----------

    @Test
    void should_notContainCarriageReturn_when_renderingBash() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "含 \r\n 换行的理由")), PUBLIC_BASE_URL);

        assertThat(script).doesNotContain("\r");
    }

    @Test
    void should_startWithShebangAndStrictMode_when_renderingBash() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).startsWith("#!/usr/bin/env bash\n");
        assertThat(script).contains("set -euo pipefail");
    }

    @Test
    void should_embedLowercaseSha256AndDownloadUrl_when_renderingBash() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains(SHA256_UPPER.toLowerCase(java.util.Locale.ROOT));
        assertThat(script).contains(PUBLIC_BASE_URL + "/api/v1/skills/1/versions/1.2.0/download");
        assertThat(script).contains("actual_sha256\" != \"$expected_sha256");
    }

    @Test
    void should_useHomeDirectory_when_bashScopeIsGlobal() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains("SKILLS_ROOT=\"$HOME/.claude/skills\"");
    }

    @Test
    void should_useCurrentWorkingDirectory_when_bashScopeIsProject() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CODEX, InstallScope.PROJECT),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains("SKILLS_ROOT=\"$(pwd)/.codex/skills\"");
    }

    @Test
    void should_includeBackupLogicBeforeOverwrite_when_renderingBash() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains(".bak.");
        assertThat(script).contains("mv \"$target_dir\" \"$backup_dir\"");
    }

    @Test
    void should_requireForceFlagAndPrintNote_when_anyArtifactIsRevokedInBash() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "存在安全隐患")), PUBLIC_BASE_URL);

        assertThat(script).contains("已被撤销");
        assertThat(script).contains("存在安全隐患");
        assertThat(script).contains("--force");
        assertThat(script).contains("exit 1");
    }

    @Test
    void should_printNoticeButNotAbort_when_skillIsUnlistedInBash() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.UNLISTED, Certification.NONE, null)), PUBLIC_BASE_URL);

        assertThat(script).contains("已下架");
        assertThat(script).contains("install_one_skill 'code-review'");
        assertThat(script).doesNotContain("HAS_REVOKED");
        assertThat(script).doesNotContain("已被撤销");
    }

    @Test
    void should_escapeSingleQuoteInCertifyNote_when_renderingBash() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "it's unsafe")), PUBLIC_BASE_URL);

        assertThat(script).contains("it'\"'\"'s unsafe");
    }

    @Test
    void should_throw_when_bashArtifactListIsEmpty() {
        assertThatThrownBy(() -> InstallScriptRenderer.renderBash(
                        bundle(ClientCode.CLAUDE, InstallScope.GLOBAL), List.of(), PUBLIC_BASE_URL))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 撤销警示必须排在确认提示之前 —— 否则带 {@code -Force} / {@code --force} 运行时,用户是先按下 y 确认、
     * 之后才看到"该 skill 的认证已被撤销",那次确认就是不知情的。而平台认证是信任链上唯一覆盖<b>内容语义</b>
     * 的一环(业务建模文档 3.6),把它放在确认之后等于让它在最关键的那一刻缺席。
     *
     * <p>用下标先后而不是 {@code contains} 断言,是因为这两段文本无论怎么排都同时存在,只有顺序会错。
     */
    @Test
    void should_warnBeforeConfirmPrompt_when_powerShellHasRevokedSkill() {
        String script = InstallScriptRenderer.renderPowerShell(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "有远程执行风险")),
                PUBLIC_BASE_URL);

        assertThat(script.indexOf("已被撤销")).isLessThan(script.indexOf("确认安装以上 skill"));
    }

    @Test
    void should_warnBeforeConfirmPrompt_when_bashHasRevokedSkill() {
        String script = InstallScriptRenderer.renderBash(
                bundle(ClientCode.CLAUDE, InstallScope.GLOBAL),
                List.of(artifact(SkillStatus.PUBLISHED, Certification.REVOKED, "有远程执行风险")),
                PUBLIC_BASE_URL);

        assertThat(script.indexOf("已被撤销")).isLessThan(script.indexOf("确认安装以上 skill"));
    }
}
