package com.landray.skillhub.application.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.repository.SkillArtifactRepository;
import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/** Mockito mock 掉仓储与制品存储接口——不起 Spring、不碰文件系统(backend/CLAUDE.md 8.1)。 */
@ExtendWith(MockitoExtension.class)
class SkillDownloadApplicationServiceTest {

    @Mock
    private SkillArtifactRepository skillArtifactRepository;

    @Mock
    private ArtifactStorage artifactStorage;

    @Mock
    private DownloadDedupGuard downloadDedupGuard;

    private SkillDownloadApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SkillDownloadApplicationService(skillArtifactRepository, artifactStorage, downloadDedupGuard);
    }

    private SkillArtifact artifact() {
        return new SkillArtifact(
                10L, 1L, "code-review", "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), 1024L,
                SkillStatus.PUBLISHED, Certification.NONE, null);
    }

    @Test
    @DisplayName("版本不存在时返回 404")
    void should_throwNotFound_when_versionDoesNotExist() {
        when(skillArtifactRepository.findBySkillIdAndVersion(1L, "9.9.9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveArtifact(1L, "9.9.9")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("resolveArtifact 找到时原样返回")
    void should_returnArtifact_when_versionExists() {
        when(skillArtifactRepository.findBySkillIdAndVersion(1L, "1.0.0")).thenReturn(Optional.of(artifact()));

        SkillArtifact resolved = service.resolveArtifact(1L, "1.0.0");

        assertThat(resolved.skillName()).isEqualTo("code-review");
    }

    @Test
    @DisplayName("openArtifact 委托给 ArtifactStorage,不直连文件系统")
    void should_delegateToArtifactStorage_when_openingArtifact() {
        InputStream stream = new ByteArrayInputStream(new byte[] {1, 2, 3});
        when(artifactStorage.openRead("artifacts/1/1.0.0.zip")).thenReturn(stream);

        InputStream result = service.openArtifact("artifacts/1/1.0.0.zip");

        assertThat(result).isSameAs(stream);
    }

    @Test
    @DisplayName("sizeOf 委托给 ArtifactStorage")
    void should_delegateToArtifactStorage_when_queryingSize() {
        when(artifactStorage.sizeOf("artifacts/1/1.0.0.zip")).thenReturn(1024L);

        assertThat(service.sizeOf("artifacts/1/1.0.0.zip")).isEqualTo(1024L);
    }

    @Test
    @DisplayName("时间窗去重放行时才真正自增计数")
    void should_incrementCount_when_dedupGuardAllows() {
        when(downloadDedupGuard.shouldCount(eq("10.0.0.1"), eq(10L), any(Instant.class))).thenReturn(true);

        service.recordDownload(1L, 10L, "10.0.0.1");

        verify(skillArtifactRepository).incrementDownloadCount(1L);
    }

    @Test
    @DisplayName("时间窗去重拒绝时不自增计数")
    void should_notIncrementCount_when_dedupGuardRejects() {
        when(downloadDedupGuard.shouldCount(eq("10.0.0.1"), eq(10L), any(Instant.class))).thenReturn(false);

        service.recordDownload(1L, 10L, "10.0.0.1");

        verify(skillArtifactRepository, never()).incrementDownloadCount(anyLong());
    }
}
