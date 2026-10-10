package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.exception.InvalidSkillSortException;
import com.landray.skillhub.domain.exception.InvalidSkillSourceException;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.NotPreviewableReason;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.SkillVersionFile;
import com.landray.skillhub.domain.model.SkillVersionFileContent;
import com.landray.skillhub.domain.model.SkillVersionFilePreviewer;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;
import com.landray.skillhub.shared.model.PageResult;

/**
 * Mockito mock 掉仓储接口,只验编排逻辑与参数规范化——不起 Spring、不碰 DB(backend/CLAUDE.md 8.1)。
 */
@ExtendWith(MockitoExtension.class)
class SkillApplicationServiceTest {

    private static final String ARTIFACT_KEY = "artifacts/1/1.0.0.zip";

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private SkillTagAssignmentService tagAssignmentService;

    @Mock
    private AuditLog auditLog;

    @Mock
    private ArtifactStorage artifactStorage;

    private SkillApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SkillApplicationService(skillRepository, tagAssignmentService, auditLog, artifactStorage);
    }

    private Skill sampleSkill() {
        return new Skill(
                1L,
                new SkillName("code-review"),
                "代码审查助手",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(2L, "张三", "80380486"),
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

    private Skill sampleSkillWithLatestVersion(String artifactKey) {
        SkillVersion latestVersion = new SkillVersion(
                10L, 1L, "1.0.0", artifactKey, null, null, 1024L, 2, List.of(), null, null, null,
                Certification.NONE, null, null, null, 1L, null);
        return new Skill(
                1L,
                new SkillName("code-review"),
                "代码审查助手",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(2L, "张三", "80380486"),
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
                latestVersion,
                List.of());
    }

    private ByteArrayInputStream buildZipStream(String topDir) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(topDir + "/SKILL.md"));
            zos.write("skill md".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    @Test
    @DisplayName("page 为 null 时默认第 1 页")
    void should_defaultPageToOne_when_pageParamIsNull() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("page 小于 1 时回落到第 1 页,而不是把非法值原样传给仓储")
    void should_defaultPageToOne_when_pageParamIsBelowMinimum() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, 0, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("size 为 null 时默认 20")
    void should_defaultSizeToTwenty_when_sizeParamIsNull() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("size 超过 100 时被夹到 100,不是拒绝请求")
    void should_clampSizeToMaximum_when_sizeParamExceedsLimit() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 100));

        service.listSkills(null, null, false, null, null, null, 1, 500);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size 小于等于 0 时回落到默认值 20")
    void should_defaultSizeToTwenty_when_sizeParamIsNotPositive() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, 1, 0);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("source 参数解析成功时传给仓储的是解析后的枚举")
    void should_parseSourceEnum_when_sourceParamIsValid() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills("internal", null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(SkillSource.INTERNAL);
    }

    @Test
    @DisplayName("source 参数不合法时在到达仓储前就抛出业务异常")
    void should_throwBeforeCallingRepository_when_sourceParamIsInvalid() {
        assertThatThrownBy(() -> service.listSkills("nowhere", null, false, null, null, null, null, null))
                .isInstanceOf(InvalidSkillSourceException.class);
        verifyNoInteractions(skillRepository);
    }

    @Test
    @DisplayName("sort 参数不合法时在到达仓储前就抛出业务异常")
    void should_throwBeforeCallingRepository_when_sortParamIsInvalid() {
        assertThatThrownBy(() -> service.listSkills(null, null, false, null, null, "popularity", null, null))
                .isInstanceOf(InvalidSkillSortException.class);
        verifyNoInteractions(skillRepository);
    }

    @Test
    @DisplayName("sort 参数为空时默认 LATEST")
    void should_defaultSortToLatest_when_sortParamIsNull() {
        when(skillRepository.search(any())).thenReturn(new PageResult<>(List.of(), 0L, 1, 20));

        service.listSkills(null, null, false, null, null, null, null, null);

        ArgumentCaptor<SkillSearchCriteria> captor = ArgumentCaptor.forClass(SkillSearchCriteria.class);
        verify(skillRepository).search(captor.capture());
        assertThat(captor.getValue().sort()).isEqualTo(SkillSortOption.LATEST);
    }

    @Test
    @DisplayName("详情命中时直接返回领域模型")
    void should_returnSkill_when_detailFound() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));

        Skill result = service.getSkillDetail(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("详情未命中时抛出 404 语义的资源不存在异常")
    void should_throwResourceNotFound_when_detailMissing() {
        when(skillRepository.findDetailById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSkillDetail(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).httpStatus())
                        .isEqualTo(404));
    }

    // ------------------------------------------------------------------
    // getLatestVersionFileManifest:详情页文件树,现读制品 zip 中央目录
    // ------------------------------------------------------------------

    @Test
    @DisplayName("latestVersion 为 null 时直接返回空清单,不碰存储")
    void should_returnEmptyManifest_when_latestVersionIsNull() {
        List<SkillVersionFile> files = service.getLatestVersionFileManifest(sampleSkill());

        assertThat(files).isEmpty();
        verifyNoInteractions(artifactStorage);
    }

    @Test
    @DisplayName("latestVersion 存在但 artifactKey 为空时返回空清单,不碰存储")
    void should_returnEmptyManifest_when_artifactKeyIsNull() {
        List<SkillVersionFile> files = service.getLatestVersionFileManifest(sampleSkillWithLatestVersion(null));

        assertThat(files).isEmpty();
        verifyNoInteractions(artifactStorage);
    }

    @Test
    @DisplayName("制品可读时返回剥壳后的文件清单")
    void should_returnManifest_when_artifactReadable() throws IOException {
        when(artifactStorage.openRead(ARTIFACT_KEY)).thenReturn(buildZipStream("code-review"));

        List<SkillVersionFile> files =
                service.getLatestVersionFileManifest(sampleSkillWithLatestVersion(ARTIFACT_KEY));

        assertThat(files).containsExactly(new SkillVersionFile("SKILL.md", 8L));
    }

    @Test
    @DisplayName("制品不存在时降级为空清单,不让详情接口整体失败")
    void should_returnEmptyManifest_when_artifactMissing() {
        when(artifactStorage.openRead(ARTIFACT_KEY))
                .thenThrow(new ResourceNotFoundException("ARTIFACT_NOT_FOUND", "制品不存在, artifactKey=" + ARTIFACT_KEY));

        List<SkillVersionFile> files =
                service.getLatestVersionFileManifest(sampleSkillWithLatestVersion(ARTIFACT_KEY));

        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("制品不是合法 zip 时降级为空清单,不让详情接口整体失败")
    void should_returnEmptyManifest_when_artifactIsCorruptZip() {
        when(artifactStorage.openRead(ARTIFACT_KEY))
                .thenReturn(new ByteArrayInputStream("不是 zip".getBytes(StandardCharsets.UTF_8)));

        List<SkillVersionFile> files =
                service.getLatestVersionFileManifest(sampleSkillWithLatestVersion(ARTIFACT_KEY));

        assertThat(files).isEmpty();
    }

    @Test
    @DisplayName("版本列表:skill 不存在时抛出 404,不再去查版本表")
    void should_throwResourceNotFound_when_listingVersionsForMissingSkill() {
        when(skillRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.listVersions(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // getVersionFileContent:版本内单个文件的预览内容(backend/CLAUDE.md 8.3 安全用例——正常路径之外的失败用例)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("路径安全且文件存在时返回预览内容,深层嵌套路径(多级目录)同样精确匹配")
    void should_returnFileContent_when_pathIsSafeAndFileExistsAtNestedPath() throws IOException {
        String nestedPath = "assets/animations/fx/firework.js";
        when(skillRepository.findVersionArtifactKey(1L, 10L)).thenReturn(Optional.of(ARTIFACT_KEY));
        when(artifactStorage.openRead(ARTIFACT_KEY))
                .thenReturn(buildZipStreamWithFile("code-review", nestedPath, "console.log('boom');"));

        SkillVersionFileContent result = service.getVersionFileContent(1L, 10L, nestedPath);

        assertThat(result.previewable()).isTrue();
        assertThat(result.path()).isEqualTo(nestedPath);
        assertThat(result.content()).isEqualTo("console.log('boom');");
    }

    @Test
    @DisplayName("path 含 .. 时在到达仓储前就被拒绝,统一表现为 404,不碰存储")
    void should_throwFileNotFound_when_pathContainsDotDotSegment() {
        assertThatThrownBy(() -> service.getVersionFileContent(1L, 10L, "../../etc/passwd"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).httpStatus())
                        .isEqualTo(404));
        verifyNoInteractions(skillRepository, artifactStorage);
    }

    @Test
    @DisplayName("path 是绝对路径时被拒绝,不碰存储")
    void should_throwFileNotFound_when_pathIsAbsolute() {
        assertThatThrownBy(() -> service.getVersionFileContent(1L, 10L, "/etc/passwd"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(skillRepository, artifactStorage);
    }

    @Test
    @DisplayName("path 含控制字符时被拒绝,不碰存储")
    void should_throwFileNotFound_when_pathContainsControlCharacter() {
        assertThatThrownBy(() -> service.getVersionFileContent(1L, 10L, "references/guide\0.md"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(skillRepository, artifactStorage);
    }

    @Test
    @DisplayName("versionId 不属于该 skillId 时返回 404,不能用 A 的 skillId 配 B 的 versionId 读到别人的文件")
    void should_throwVersionNotFound_when_versionDoesNotBelongToSkill() {
        when(skillRepository.findVersionArtifactKey(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVersionFileContent(1L, 10L, "SKILL.md"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(artifactStorage);
    }

    @Test
    @DisplayName("请求的路径不在清单里时返回 404")
    void should_throwFileNotFound_when_pathNotInManifest() throws IOException {
        when(skillRepository.findVersionArtifactKey(1L, 10L)).thenReturn(Optional.of(ARTIFACT_KEY));
        when(artifactStorage.openRead(ARTIFACT_KEY)).thenReturn(buildZipStream("code-review"));

        assertThatThrownBy(() -> service.getVersionFileContent(1L, 10L, "not-exist.md"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("制品缺失时返回 404,而不是让整个请求 500")
    void should_throwFileNotFound_when_artifactMissing() {
        when(skillRepository.findVersionArtifactKey(1L, 10L)).thenReturn(Optional.of(ARTIFACT_KEY));
        when(artifactStorage.openRead(ARTIFACT_KEY))
                .thenThrow(new ResourceNotFoundException("ARTIFACT_NOT_FOUND", "制品不存在, artifactKey=" + ARTIFACT_KEY));

        assertThatThrownBy(() -> service.getVersionFileContent(1L, 10L, "SKILL.md"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("制品不是合法 zip 时返回 404,而不是让整个请求 500")
    void should_throwFileNotFound_when_artifactIsCorruptZip() {
        when(skillRepository.findVersionArtifactKey(1L, 10L)).thenReturn(Optional.of(ARTIFACT_KEY));
        when(artifactStorage.openRead(ARTIFACT_KEY))
                .thenReturn(new ByteArrayInputStream("不是 zip".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> service.getVersionFileContent(1L, 10L, "SKILL.md"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("超过 256KB 的文件返回 previewable=false,原因是 TOO_LARGE")
    void should_returnNotPreviewable_when_fileExceedsSizeLimit() throws IOException {
        byte[] oversized = new byte[(int) SkillVersionFilePreviewer.MAX_PREVIEWABLE_BYTES + 1];
        when(skillRepository.findVersionArtifactKey(1L, 10L)).thenReturn(Optional.of(ARTIFACT_KEY));
        when(artifactStorage.openRead(ARTIFACT_KEY))
                .thenReturn(buildZipStreamWithFile("code-review", "scripts/big.sh", oversized));

        SkillVersionFileContent result = service.getVersionFileContent(1L, 10L, "scripts/big.sh");

        assertThat(result.previewable()).isFalse();
        assertThat(result.notPreviewableReason()).isEqualTo(NotPreviewableReason.TOO_LARGE);
    }

    @Test
    @DisplayName("含 NUL 字节的文件返回 previewable=false,原因是 BINARY,即使扩展名是 .md")
    void should_returnNotPreviewable_when_fileContainsNulByte() throws IOException {
        byte[] binaryLikeMd = new byte[] {'a', 'b', 0x00, 'c'};
        when(skillRepository.findVersionArtifactKey(1L, 10L)).thenReturn(Optional.of(ARTIFACT_KEY));
        when(artifactStorage.openRead(ARTIFACT_KEY))
                .thenReturn(buildZipStreamWithFile("code-review", "SKILL.md", binaryLikeMd));

        SkillVersionFileContent result = service.getVersionFileContent(1L, 10L, "SKILL.md");

        assertThat(result.previewable()).isFalse();
        assertThat(result.notPreviewableReason()).isEqualTo(NotPreviewableReason.BINARY);
    }

    private ByteArrayInputStream buildZipStreamWithFile(String topDir, String relativePath, String content)
            throws IOException {
        return buildZipStreamWithFile(topDir, relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    private ByteArrayInputStream buildZipStreamWithFile(String topDir, String relativePath, byte[] content)
            throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(topDir + "/" + relativePath));
            zos.write(content);
            zos.closeEntry();
        }
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    @Test
    @DisplayName("版本列表:skill 存在时返回仓储查到的版本")
    void should_returnVersions_when_skillExists() {
        when(skillRepository.existsById(1L)).thenReturn(true);
        SkillVersion version = new SkillVersion(
                1L, 1L, "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), "0".repeat(64), 100L, 1, List.of(), null,
                null, null, Certification.NONE, null, null, null, 1L, null);
        when(skillRepository.findVersionsBySkillId(1L)).thenReturn(List.of(version));

        List<SkillVersion> result = service.listVersions(1L);

        assertThat(result).containsExactly(version);
    }

    // ------------------------------------------------------------------
    // updateMetadata / unlist / relist
    // ------------------------------------------------------------------

    @Test
    @DisplayName("改元数据:owner 本人可以操作")
    void should_updateMetadata_when_actingUserIsOwner() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        when(tagAssignmentService.resolveTagIds(List.of(1L), List.of(), 2L)).thenReturn(List.of(1L));
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        Skill result =
                service.updateMetadata(1L, "新展示名", "community", List.of(1L), List.of(), owner);

        assertThat(result).isNotNull();
        verify(skillRepository).updateMetadata(1L, "新展示名", SkillSource.COMMUNITY);
        verify(tagAssignmentService).replaceSkillTags(1L, List.of(1L));
    }

    @Test
    @DisplayName("改元数据:ADMIN 可以操作非自己拥有的 skill")
    void should_updateMetadata_when_actingUserIsAdmin() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        when(tagAssignmentService.resolveTagIds(any(), any(), anyLong())).thenReturn(List.of(1L));
        AuthenticatedUser admin = new AuthenticatedUser(999L, UserRole.ADMIN, 10L, 100L);

        assertThat(service.updateMetadata(1L, "新展示名", "community", List.of(1L), List.of(), admin))
                .isNotNull();
    }

    @Test
    @DisplayName("改元数据:既非 owner 也非 ADMIN 时拒绝")
    void should_rejectUpdateMetadata_when_actingUserIsNeitherOwnerNorAdmin() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, UserRole.USER, 10L, 100L);

        assertThatThrownBy(() -> service.updateMetadata(1L, "新展示名", "community", List.of(1L), List.of(), stranger))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tagAssignmentService);
    }

    @Test
    @DisplayName("改元数据:skill 不存在时返回 404")
    void should_throwNotFound_when_updatingMissingSkill() {
        when(skillRepository.findDetailById(999L)).thenReturn(Optional.empty());
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        assertThatThrownBy(() -> service.updateMetadata(999L, "x", "internal", List.of(1L), List.of(), owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("下架:owner 可以操作,只改 status")
    void should_unlistSkill_when_actingUserIsOwner() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        assertThat(service.unlist(1L, owner)).isNotNull();

        verify(skillRepository).updateStatus(1L, SkillStatus.UNLISTED);
    }

    @Test
    @DisplayName("下架:既非 owner 也非 ADMIN 时拒绝")
    void should_rejectUnlist_when_actingUserIsNeitherOwnerNorAdmin() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, UserRole.USER, 10L, 100L);

        assertThatThrownBy(() -> service.unlist(1L, stranger)).isInstanceOf(AccessDeniedException.class);
        verify(skillRepository, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("恢复:owner 可以操作,只改 status")
    void should_relistSkill_when_actingUserIsOwner() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        assertThat(service.relist(1L, owner)).isNotNull();

        verify(skillRepository).updateStatus(1L, SkillStatus.PUBLISHED);
    }

    // ------------------------------------------------------------------
    // 物理删除:不可逆,所以权限、顺序、失败方向都要钉死
    // ------------------------------------------------------------------

    @Test
    @DisplayName("删除:owner 可以操作,库行与制品文件都清掉")
    void should_deleteSkillAndArtifacts_when_actingUserIsOwner() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        when(skillRepository.findVersionArtifactKeys(1L)).thenReturn(List.of(ARTIFACT_KEY, "artifacts/1/1.1.0.zip"));
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        service.delete(1L, owner);

        verify(skillRepository).deleteSkillCascade(1L);
        verify(artifactStorage).delete(ARTIFACT_KEY);
        verify(artifactStorage).delete("artifacts/1/1.1.0.zip");
    }

    @Test
    @DisplayName("删除:ADMIN 可以删别人的 skill")
    void should_deleteSkill_when_actingUserIsAdmin() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        when(skillRepository.findVersionArtifactKeys(1L)).thenReturn(List.of());
        AuthenticatedUser admin = new AuthenticatedUser(999L, UserRole.ADMIN, 10L, 100L);

        service.delete(1L, admin);

        verify(skillRepository).deleteSkillCascade(1L);
    }

    @Test
    @DisplayName("删除:既非 owner 也非 ADMIN 时拒绝,一行一个字节都不许动")
    void should_rejectDelete_when_actingUserIsNeitherOwnerNorAdmin() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        AuthenticatedUser stranger = new AuthenticatedUser(999L, UserRole.USER, 10L, 100L);

        assertThatThrownBy(() -> service.delete(1L, stranger)).isInstanceOf(AccessDeniedException.class);

        verify(skillRepository, never()).deleteSkillCascade(anyLong());
        verifyNoInteractions(artifactStorage);
    }

    @Test
    @DisplayName("删除:skill 不存在时 404,不做任何删除")
    void should_rejectDelete_when_skillNotFound() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.empty());
        AuthenticatedUser admin = new AuthenticatedUser(999L, UserRole.ADMIN, 10L, 100L);

        assertThatThrownBy(() -> service.delete(1L, admin)).isInstanceOf(ResourceNotFoundException.class);

        verify(skillRepository, never()).deleteSkillCascade(anyLong());
        verifyNoInteractions(artifactStorage);
    }

    @Test
    @DisplayName("删除:必须先取 artifactKey 再删库行——反过来就再也不知道该删哪些文件")
    void should_collectArtifactKeys_beforeDeletingRows() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        when(skillRepository.findVersionArtifactKeys(1L)).thenReturn(List.of(ARTIFACT_KEY));
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        service.delete(1L, owner);

        // 顺序本身就是需求:这两步换个位置,代码照样跑通、测试照样绿(如果不验顺序的话),
        // 而线上表现是制品文件永久残留 —— 没有任何报错,只是磁盘一直涨。
        InOrder inOrder = inOrder(skillRepository, artifactStorage);
        inOrder.verify(skillRepository).findVersionArtifactKeys(1L);
        inOrder.verify(skillRepository).deleteSkillCascade(1L);
        inOrder.verify(artifactStorage).delete(ARTIFACT_KEY);
    }

    @Test
    @DisplayName("删除:标签走 replaceSkillTags(空集合),复用孤儿 FREE 标签清理规则")
    void should_clearTagsThroughAssignmentService_when_deleted() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        when(skillRepository.findVersionArtifactKeys(1L)).thenReturn(List.of());
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        service.delete(1L, owner);

        // 不自己写一遍删标签的 SQL:孤儿 FREE 标签的清理规则(5.7)只该有一处实现,
        // 删除路径漏掉它的话,/tags/suggest 会一直把死词条推给用户。
        verify(tagAssignmentService).replaceSkillTags(1L, List.of());
    }

    @Test
    @DisplayName("删除:某个制品文件删不掉时,剩下的照删,不让一个文件卡住整轮收尾")
    void should_keepDeletingRemainingArtifacts_when_oneArtifactFails() {
        when(skillRepository.findDetailById(1L)).thenReturn(Optional.of(sampleSkill()));
        when(skillRepository.findVersionArtifactKeys(1L)).thenReturn(List.of("bad.zip", "good.zip"));
        doThrow(new UncheckedIOException("制品删除失败", new IOException("被占用")))
                .when(artifactStorage)
                .delete("bad.zip");
        AuthenticatedUser owner = new AuthenticatedUser(2L, UserRole.USER, 10L, 100L);

        // 库行此时已经删了,再把异常抛给调用方只会让前端显示"删除失败"而实际上已经删掉了 ——
        // 那是最糟的一种反馈。留下的孤儿文件是垃圾,不是数据损坏。
        service.delete(1L, owner);

        verify(artifactStorage).delete("good.zip");
    }
}
