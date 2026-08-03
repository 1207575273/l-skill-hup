package com.landray.skillhub.application.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.application.SkillTagAssignmentService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.exception.SkillVersionNameMismatchException;
import com.landray.skillhub.domain.exception.SkillVersionNotIncreasingException;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

@ExtendWith(MockitoExtension.class)
class SkillUploadApplicationServiceTest {

    private static final UploadProperties GENEROUS_PROPERTIES =
            new UploadProperties(64L * 1024 * 1024, 500, 16L * 1024 * 1024, 8, 255, 100);

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private ArtifactStorage artifactStorage;

    @Mock
    private SkillTagAssignmentService tagAssignmentService;

    @Mock
    private AuditLog auditLog;

    private SkillUploadApplicationService service() {
        return new SkillUploadApplicationService(
                GENEROUS_PROPERTIES, skillRepository, artifactStorage, tagAssignmentService, auditLog);
    }

    private Skill sampleExistingSkill(long ownerId) {
        return new Skill(
                42L,
                new SkillName("code-review"),
                "代码审查",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(ownerId, "张三", "80380486"),
                "摘要",
                SkillStatus.PUBLISHED,
                0L,
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now(),
                new SkillVersion(
                        1L, 42L, "1.2.0", "code-review/1.2.0/xxx.zip", "d".repeat(64), "s".repeat(64), 100L, 1,
                        List.of(), "content", null, null, null, null, null, null, null, null),
                List.of());
    }

    // ------------------------------------------------------------------
    // precheck
    // ------------------------------------------------------------------

    @Test
    @DisplayName("包校验失败时返回 valid=false,不抛异常")
    void should_returnInvalidResult_when_packageFailsValidation() throws IOException {
        byte[] zip = buildZip(Map.of("README.md", "只有 README,没有 SKILL.md"));

        PrecheckResult result = service().precheck(zip, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).code()).isEqualTo("SKILL_PACKAGE_STRUCTURE_INVALID");
    }

    @Test
    @DisplayName("没有重名与相同内容时返回干净的 valid 结果")
    void should_returnCleanResult_when_noCollision() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述")));
        when(skillRepository.findVersionByContentDigest(anyString())).thenReturn(Optional.empty());
        when(skillRepository.findByName("foo")).thenReturn(Optional.empty());

        PrecheckResult result = service().precheck(zip, null);

        assertThat(result.valid()).isTrue();
        assertThat(result.duplicatedName()).isFalse();
        assertThat(result.identical()).isNull();
        assertThat(result.parsed().name()).isEqualTo("foo");
    }

    @Test
    @DisplayName("content_digest 命中已有版本时返回 identical")
    void should_returnIdenticalMatch_when_contentDigestAlreadyExists() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述")));
        when(skillRepository.findVersionByContentDigest(anyString()))
                .thenReturn(Optional.of(new SkillVersion(
                        1L, 17L, "1.0.0", null, null, null, 0L, 0, List.of(), null, null, null, null, null, null,
                        null, null, null)));
        when(skillRepository.findByName("foo")).thenReturn(Optional.empty());

        PrecheckResult result = service().precheck(zip, null);

        assertThat(result.identical()).isNotNull();
        assertThat(result.identical().skillId()).isEqualTo(17L);
        assertThat(result.identical().version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("重名且当前用户是 owner 时 canPublishNewVersion 为 true,不给建议名")
    void should_allowPublishNewVersion_when_actingUserIsOwner() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "描述")));
        when(skillRepository.findVersionByContentDigest(anyString())).thenReturn(Optional.empty());
        when(skillRepository.findByName("code-review")).thenReturn(Optional.of(sampleExistingSkill(9L)));

        PrecheckResult result = service().precheck(zip, new AuthenticatedUser(9L, UserRole.USER, 1L, 1L));

        assertThat(result.duplicatedName()).isTrue();
        assertThat(result.existing().canPublishNewVersion()).isTrue();
    }

    @Test
    @DisplayName("重名但当前用户既非 owner 也非 ADMIN 时不能发新版本,给出建议名")
    void should_disallowPublishNewVersion_when_actingUserIsNeitherOwnerNorAdmin() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "描述")));
        when(skillRepository.findVersionByContentDigest(anyString())).thenReturn(Optional.empty());
        when(skillRepository.findByName("code-review")).thenReturn(Optional.of(sampleExistingSkill(9L)));
        when(skillRepository.findByName(startsWith("code-review-")))
                .thenReturn(Optional.empty());

        PrecheckResult result = service().precheck(zip, new AuthenticatedUser(2L, UserRole.USER, 1L, 1L));

        assertThat(result.existing().canPublishNewVersion()).isFalse();
        assertThat(result.suggestedName()).startsWith("code-review-");
    }

    @Test
    @DisplayName("匿名用户(actingUser 为 null)遇到重名时不能发新版本")
    void should_disallowPublishNewVersion_when_actingUserIsAnonymous() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "描述")));
        when(skillRepository.findVersionByContentDigest(anyString())).thenReturn(Optional.empty());
        when(skillRepository.findByName("code-review")).thenReturn(Optional.of(sampleExistingSkill(9L)));
        when(skillRepository.findByName(startsWith("code-review-")))
                .thenReturn(Optional.empty());

        PrecheckResult result = service().precheck(zip, null);

        assertThat(result.existing().canPublishNewVersion()).isFalse();
    }

    // ------------------------------------------------------------------
    // publishNewSkill
    // ------------------------------------------------------------------

    @Test
    @DisplayName("发布新 skill:落库、存制品、打标签、置 latest_version 全部按顺序完成")
    void should_publishNewSkill_when_noNameCollision() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "一段描述")));
        when(skillRepository.findByName("foo")).thenReturn(Optional.empty());
        when(tagAssignmentService.resolveTagIds(List.of(1L), List.of(), 9L)).thenReturn(List.of(1L));
        when(skillRepository.insertSkill(
                        eq(new SkillName("foo")), eq("展示名"), eq("一段描述"), eq(SkillSource.INTERNAL), eq(9L), any()))
                .thenReturn(100L);
        when(skillRepository.insertVersion(any())).thenReturn(200L);
        when(skillRepository.findDetailById(100L)).thenReturn(Optional.of(sampleExistingSkill(9L)));

        NewSkillCommand command =
                new NewSkillCommand(zip, "1.0.0", "展示名", "internal", List.of(1L), List.of(), 9L);
        Skill result = service().publishNewSkill(command);

        assertThat(result).isNotNull();
        verify(artifactStorage)
                .store(argThat(key -> key.startsWith("foo/1.0.0/") && key.endsWith(".zip")), any());
        verify(tagAssignmentService).replaceSkillTags(100L, List.of(1L));
        verify(skillRepository).updateLatestVersionIfGreater(eq(100L), eq(200L), eq("1.0.0"), eq("一段描述"), any());
        verify(auditLog).info(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("displayName 留空时回退成最终 name")
    void should_fallbackDisplayNameToFinalName_when_displayNameBlank() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述")));
        when(skillRepository.findByName("foo")).thenReturn(Optional.empty());
        when(tagAssignmentService.resolveTagIds(any(), any(), anyLong())).thenReturn(List.of(1L));
        when(skillRepository.insertVersion(any())).thenReturn(200L);
        when(skillRepository.findDetailById(anyLong())).thenReturn(Optional.of(sampleExistingSkill(9L)));

        NewSkillCommand command = new NewSkillCommand(zip, "1.0.0", "  ", "internal", List.of(1L), List.of(), 9L);
        service().publishNewSkill(command);

        verify(skillRepository).insertSkill(eq(new SkillName("foo")), eq("foo"), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("发布时目标名已被占用,自动升级到短哈希后缀名")
    void should_useSuggestedName_when_baseNameAlreadyTaken() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述")));
        when(skillRepository.findByName("foo")).thenReturn(Optional.of(sampleExistingSkill(1L)));
        when(skillRepository.findByName(startsWith("foo-"))).thenReturn(Optional.empty());
        when(tagAssignmentService.resolveTagIds(any(), any(), anyLong())).thenReturn(List.of(1L));
        when(skillRepository.insertVersion(any())).thenReturn(200L);
        when(skillRepository.findDetailById(anyLong())).thenReturn(Optional.of(sampleExistingSkill(9L)));

        NewSkillCommand command = new NewSkillCommand(zip, "1.0.0", "展示名", "internal", List.of(1L), List.of(), 9L);
        service().publishNewSkill(command);

        verify(skillRepository)
                .insertSkill(
                        argThat(name -> name.value().startsWith("foo-")), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("目标名及全部短哈希后缀都冲突时拒绝")
    void should_rejectPublish_when_allSuffixCandidatesCollide() throws IOException {
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述")));
        Skill taken = sampleExistingSkill(1L);
        when(skillRepository.findByName(anyString())).thenReturn(Optional.of(taken));
        when(tagAssignmentService.resolveTagIds(any(), any(), anyLong())).thenReturn(List.of(1L));

        NewSkillCommand command = new NewSkillCommand(zip, "1.0.0", "展示名", "internal", List.of(1L), List.of(), 9L);

        assertThatThrownBy(() -> service().publishNewSkill(command))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> assertThat(((ResourceConflictException) ex).code()).isEqualTo("SKILL_NAME_DUPLICATED"));
        verify(artifactStorage, never()).store(any(), any());
    }

    // ------------------------------------------------------------------
    // publishNewVersion
    // ------------------------------------------------------------------

    private AuthenticatedUser owner() {
        return new AuthenticatedUser(9L, UserRole.USER, 1L, 1L);
    }

    private AuthenticatedUser admin() {
        return new AuthenticatedUser(1L, UserRole.ADMIN, 1L, 1L);
    }

    private AuthenticatedUser stranger() {
        return new AuthenticatedUser(999L, UserRole.USER, 1L, 1L);
    }

    @Test
    @DisplayName("目标 skill 不存在时返回 404")
    void should_throwNotFound_when_targetSkillMissing() throws IOException {
        when(skillRepository.findDetailById(999L)).thenReturn(Optional.empty());
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("foo", "描述")));

        assertThatThrownBy(() -> service().publishNewVersion(999L, new NewVersionCommand(zip, "1.0.0", owner())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("既非 owner 也非 ADMIN 时拒绝发布新版本")
    void should_rejectPublishNewVersion_when_actingUserIsNeitherOwnerNorAdmin() throws IOException {
        when(skillRepository.findDetailById(42L)).thenReturn(Optional.of(sampleExistingSkill(9L)));
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "描述")));

        assertThatThrownBy(
                        () -> service().publishNewVersion(42L, new NewVersionCommand(zip, "1.3.0", stranger())))
                .isInstanceOf(AccessDeniedException.class);
        verify(artifactStorage, never()).store(any(), any());
    }

    @Test
    @DisplayName("ADMIN 可以给非自己拥有的 skill 发布新版本")
    void should_allowPublishNewVersion_when_actingUserIsAdmin() throws IOException {
        when(skillRepository.findDetailById(42L)).thenReturn(Optional.of(sampleExistingSkill(9L)));
        when(skillRepository.insertVersion(any())).thenReturn(300L);
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "描述")));

        assertThat(service().publishNewVersion(42L, new NewVersionCommand(zip, "1.3.0", admin())))
                .isNotNull();
    }

    @Test
    @DisplayName("包内 name 与目标 skill 不一致时拒绝")
    void should_rejectPublishNewVersion_when_nameMismatch() throws IOException {
        when(skillRepository.findDetailById(42L)).thenReturn(Optional.of(sampleExistingSkill(9L)));
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("not-code-review", "描述")));

        assertThatThrownBy(() -> service().publishNewVersion(42L, new NewVersionCommand(zip, "2.0.0", owner())))
                .isInstanceOf(SkillVersionNameMismatchException.class);
    }

    @Test
    @DisplayName("新版本号不大于当前最大版本时拒绝")
    void should_rejectPublishNewVersion_when_versionNotIncreasing() throws IOException {
        when(skillRepository.findDetailById(42L)).thenReturn(Optional.of(sampleExistingSkill(9L)));
        when(skillRepository.findLatestVersionString(42L)).thenReturn(Optional.of("1.2.0"));
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "描述")));

        assertThatThrownBy(() -> service().publishNewVersion(42L, new NewVersionCommand(zip, "1.1.0", owner())))
                .isInstanceOf(SkillVersionNotIncreasingException.class);
    }

    @Test
    @DisplayName("发布新版本成功:certification 恒为 NONE,latest_version 带递增条件更新")
    void should_publishNewVersion_when_versionIsGreater() throws IOException {
        when(skillRepository.findDetailById(42L)).thenReturn(Optional.of(sampleExistingSkill(9L)));
        when(skillRepository.findLatestVersionString(42L)).thenReturn(Optional.of("1.2.0"));
        when(skillRepository.insertVersion(any())).thenReturn(300L);
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "新版本描述")));

        Skill result = service().publishNewVersion(42L, new NewVersionCommand(zip, "1.3.0", owner()));

        assertThat(result).isNotNull();
        verify(skillRepository)
                .updateLatestVersionIfGreater(eq(42L), eq(300L), eq("1.3.0"), eq("新版本描述"), any());
        verify(skillRepository).insertVersion(argThat(v -> v.certification() == Certification.NONE));
    }

    @Test
    @DisplayName("首次发布(尚无既有版本)不受递增校验阻拦")
    void should_publishFirstVersionWithoutIncreasingCheck() throws IOException {
        when(skillRepository.findDetailById(42L)).thenReturn(Optional.of(sampleExistingSkill(9L)));
        when(skillRepository.findLatestVersionString(42L)).thenReturn(Optional.empty());
        when(skillRepository.insertVersion(any())).thenReturn(300L);
        byte[] zip = buildZip(Map.of("SKILL.md", skillMd("code-review", "描述")));

        assertThat(service().publishNewVersion(42L, new NewVersionCommand(zip, "0.0.1", owner())))
                .isNotNull();
    }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    private String skillMd(String name, String description) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n\n正文\n";
    }

    private byte[] buildZip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : new LinkedHashMap<>(entries).entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
