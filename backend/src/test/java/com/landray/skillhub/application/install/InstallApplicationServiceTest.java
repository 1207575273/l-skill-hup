package com.landray.skillhub.application.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.domain.exception.ClientScopeNotSupportedException;
import com.landray.skillhub.domain.exception.InstallBundleExpiredException;
import com.landray.skillhub.domain.exception.InstallBundleItemsGoneException;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.ClientCode;
import com.landray.skillhub.domain.model.InstallBundle;
import com.landray.skillhub.domain.model.InstallItem;
import com.landray.skillhub.domain.model.InstallScope;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.repository.InstallBundleRepository;
import com.landray.skillhub.domain.repository.SkillArtifactRepository;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/** Mockito mock 掉仓储接口,只验编排逻辑——不起 Spring、不碰 DB(backend/CLAUDE.md 8.1)。 */
@ExtendWith(MockitoExtension.class)
class InstallApplicationServiceTest {

    private static final Instant NOW_ISH = Instant.parse("2026-08-03T00:00:00Z");
    private static final String PUBLIC_BASE_URL = "http://localhost:18080/skillhub";

    @Mock
    private InstallBundleRepository installBundleRepository;

    @Mock
    private SkillArtifactRepository skillArtifactRepository;

    private InstallApplicationService service;

    @BeforeEach
    void setUp() {
        service = new InstallApplicationService(
                installBundleRepository, skillArtifactRepository, new InstallProperties(PUBLIC_BASE_URL, 30));
    }

    private SkillArtifact artifact(long skillId, long versionId, Certification certification, SkillStatus status) {
        return new SkillArtifact(
                versionId, skillId, "code-review", "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), 1024L, status,
                certification, null);
    }

    @Test
    @DisplayName("GET /clients 只返回适配表里已实测的组合")
    void should_returnOnlyTestedClientTargets_when_listingSupportedClients() {
        assertThat(service.listSupportedClients()).hasSize(5).allMatch(t -> t.tested());
    }

    @Test
    @DisplayName("client/scope 组合未实测时拒绝生成安装单")
    void should_throwClientScopeNotSupported_when_combinationIsUntested() {
        assertThatThrownBy(() -> service.createBundle(
                        List.of(new InstallItem(1L, 10L)), "workbuddy", "project", null))
                .isInstanceOf(ClientScopeNotSupportedException.class);
    }

    @Test
    @DisplayName("引用不存在的 skill 版本时拒绝生成安装单")
    void should_throwNotFound_when_itemReferencesMissingVersion() {
        when(skillArtifactRepository.findByVersionIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBundle(
                        List.of(new InstallItem(1L, 10L)), "claude", "global", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("skillId 与版本实际归属的 skill 不一致时拒绝生成安装单")
    void should_throwNotFound_when_skillIdDoesNotMatchResolvedVersion() {
        SkillArtifact mismatched = artifact(999L, 10L, Certification.NONE, SkillStatus.PUBLISHED);
        when(skillArtifactRepository.findByVersionIds(any())).thenReturn(List.of(mismatched));

        assertThatThrownBy(() -> service.createBundle(
                        List.of(new InstallItem(1L, 10L)), "claude", "global", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("成功生成安装单:保存、清理过期行、返回可复制的命令要素")
    void should_saveBundleAndCleanupExpired_when_itemsAreValid() {
        SkillArtifact matched = artifact(1L, 10L, Certification.NONE, SkillStatus.PUBLISHED);
        when(skillArtifactRepository.findByVersionIds(any())).thenReturn(List.of(matched));
        when(installBundleRepository.save(any())).thenAnswer(invocation -> {
            InstallBundle candidate = invocation.getArgument(0);
            return new InstallBundle(
                    99L, candidate.token(), candidate.items(), candidate.client(), candidate.scope(),
                    candidate.createdByUserId(), candidate.createdAt(), candidate.expiresAt());
        });

        CreateBundleResult result =
                service.createBundle(List.of(new InstallItem(1L, 10L)), "claude", "global", 7L);

        assertThat(result.token()).isNotBlank();
        assertThat(result.client()).isEqualTo("claude");
        assertThat(result.scope()).isEqualTo("global");
        assertThat(result.publicBaseUrl()).isEqualTo(PUBLIC_BASE_URL);

        ArgumentCaptor<InstallBundle> savedCaptor = ArgumentCaptor.forClass(InstallBundle.class);
        verify(installBundleRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().createdByUserId()).isEqualTo(7L);
        assertThat(savedCaptor.getValue().client()).isEqualTo(ClientCode.CLAUDE);

        verify(installBundleRepository).deleteExpiredBefore(any());
    }

    @Test
    @DisplayName("token 不存在时渲染脚本报 404")
    void should_throwNotFound_when_renderingWithUnknownToken() {
        when(installBundleRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renderPowerShell("missing", "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("token 已过期时渲染脚本报 410,且不会去查询制品")
    void should_throwExpired_when_bundleExpired() {
        InstallBundle expired = new InstallBundle(
                1L, "expired-token", List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL,
                null, NOW_ISH.minusSeconds(3600), NOW_ISH.minusSeconds(1));
        when(installBundleRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.renderPowerShell("expired-token", "127.0.0.1"))
                .isInstanceOf(InstallBundleExpiredException.class);

        verify(skillArtifactRepository, never()).findByVersionIds(any());
    }

    @Test
    @DisplayName("渲染成功时按 bundle.items() 的原始顺序取制品,不依赖数据库返回顺序")
    void should_renderScript_when_bundleIsValid() {
        InstallBundle valid = new InstallBundle(
                1L, "valid-token", List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null,
                Instant.now(), Instant.now().plusSeconds(3600));
        when(installBundleRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));
        when(skillArtifactRepository.findByVersionIds(any()))
                .thenReturn(List.of(artifact(1L, 10L, Certification.CERTIFIED, SkillStatus.PUBLISHED)));

        RenderedScript script = service.renderPowerShell("valid-token", "127.0.0.1");

        assertThat(script.content()).contains("code-review");
        assertThat(script.content()).contains(PUBLIC_BASE_URL);
    }

    @Test
    @DisplayName("命中 REVOKED 版本时打审计 WARN,不是 domain 校验失败")
    void should_stillRenderScript_when_hittingRevokedVersion() {
        InstallBundle valid = new InstallBundle(
                1L, "valid-token", List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null,
                Instant.now(), Instant.now().plusSeconds(3600));
        when(installBundleRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));
        when(skillArtifactRepository.findByVersionIds(any()))
                .thenReturn(List.of(artifact(1L, 10L, Certification.REVOKED, SkillStatus.PUBLISHED)));

        RenderedScript script = service.renderBash("valid-token", "127.0.0.1");

        assertThat(script.content()).contains("已被撤销");
    }

    @Test
    @DisplayName("createBundle 校验失败时不写库、不清理")
    void should_notPersistAnything_when_validationFails() {
        when(skillArtifactRepository.findByVersionIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBundle(
                        List.of(new InstallItem(1L, 10L)), "claude", "global", null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(installBundleRepository, never()).save(any());
        verify(installBundleRepository, times(0)).deleteExpiredBefore(any());
    }

    @Test
    @DisplayName("适配表拒绝的组合在校验制品是否存在之前就已经短路,不会触发多余查询")
    void should_shortCircuitBeforeResolvingArtifacts_when_clientScopeUnsupported() {
        assertThatThrownBy(() -> service.createBundle(
                        List.of(new InstallItem(1L, 10L)), "cursor", "global", null))
                .isInstanceOf(ClientScopeNotSupportedException.class);

        verify(skillArtifactRepository, never()).findByVersionIds(any());
    }

    @Test
    @DisplayName("skillId 就是本次校验唯一的仲裁依据,即便 versionId 存在也不放过归属不一致的情况")
    void should_rejectMismatchedSkillId_even_whenVersionIdIsKnown() {
        when(skillArtifactRepository.findByVersionIds(eq(List.of(10L))))
                .thenReturn(List.of(artifact(2L, 10L, Certification.NONE, SkillStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.createBundle(
                        List.of(new InstallItem(1L, 10L)), "claude", "global", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("匿名用户(未登录)也能生成安装单,createdByUserId 为空")
    void should_allowAnonymousUser_when_creatingBundle() {
        when(skillArtifactRepository.findByVersionIds(any()))
                .thenReturn(List.of(artifact(1L, 10L, Certification.NONE, SkillStatus.PUBLISHED)));
        when(installBundleRepository.save(any())).thenAnswer(invocation -> {
            InstallBundle candidate = invocation.getArgument(0);
            return new InstallBundle(
                    1L, candidate.token(), candidate.items(), candidate.client(), candidate.scope(), null,
                    candidate.createdAt(), candidate.expiresAt());
        });

        CreateBundleResult result =
                service.createBundle(List.of(new InstallItem(1L, 10L)), "claude", "global", null);

        assertThat(result.token()).isNotBlank();
    }

    @Test
    @DisplayName("渲染时不会重复调用制品仓储超出必要次数")
    void should_queryArtifactsExactlyOnce_when_rendering() {
        InstallBundle valid = new InstallBundle(
                1L, "valid-token", List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null,
                Instant.now(), Instant.now().plusSeconds(3600));
        when(installBundleRepository.findByToken("valid-token")).thenReturn(Optional.of(valid));
        when(skillArtifactRepository.findByVersionIds(any()))
                .thenReturn(List.of(artifact(1L, 10L, Certification.NONE, SkillStatus.PUBLISHED)));

        service.renderBash("valid-token", "127.0.0.1");

        verify(skillArtifactRepository, times(1)).findByVersionIds(any());
    }

    @Test
    @DisplayName("versionId 对应制品缺失时,渲染直接跳过该项而不是抛异常(渲染是尽力而为的展示,不是写路径的强校验)")
    void should_skipMissingArtifact_when_rendering() {
        InstallBundle bundle = new InstallBundle(
                1L, "valid-token",
                List.of(new InstallItem(1L, 10L), new InstallItem(2L, 20L)), ClientCode.CLAUDE,
                InstallScope.GLOBAL, null, Instant.now(), Instant.now().plusSeconds(3600));
        when(installBundleRepository.findByToken("valid-token")).thenReturn(Optional.of(bundle));
        when(skillArtifactRepository.findByVersionIds(any()))
                .thenReturn(List.of(artifact(1L, 10L, Certification.NONE, SkillStatus.PUBLISHED)));

        RenderedScript script = service.renderPowerShell("valid-token", "127.0.0.1");

        assertThat(script.content()).contains("code-review");
        // 跳过归跳过,但必须在脚本里说出来。生成安装单时 validateItemsResolvable 已经确保每一条都存在,
        // 所以此刻解析不到只有一个原因:那个 skill 在这之后被物理删除了。
        assertThat(script.content()).contains("有 1 个 skill 已从平台删除");
    }

    @Test
    @DisplayName("安装单里的 skill 全被删除时返回 410,而不是渲染一个什么都不装的脚本")
    void should_throwItemsGone_when_everyItemDeleted() {
        InstallBundle bundle = new InstallBundle(
                1L, "valid-token",
                List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE,
                InstallScope.GLOBAL, null, Instant.now(), Instant.now().plusSeconds(3600));
        when(installBundleRepository.findByToken("valid-token")).thenReturn(Optional.of(bundle));
        when(skillArtifactRepository.findByVersionIds(any())).thenReturn(List.of());

        // 之前这里会掉进 requireNonEmpty 的 IllegalStateException,最终是 500 加一句
        // "渲染安装脚本时制品列表不能为空" —— 对着一条群里流传的安装命令,这句话什么也解释不了。
        // 410 与"安装单已过期"对齐:资源曾经有效、现在不再有效,正确的下一步都是回平台重新生成。
        assertThatThrownBy(() -> service.renderPowerShell("valid-token", "127.0.0.1"))
                .isInstanceOf(InstallBundleItemsGoneException.class);
    }
}
