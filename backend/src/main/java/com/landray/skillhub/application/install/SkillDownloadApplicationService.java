package com.landray.skillhub.application.install;

import java.io.InputStream;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.repository.SkillArtifactRepository;
import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/**
 * {@code GET /skills/{id}/versions/{version}/download} 用例编排(业务建模文档 5.12)。
 *
 * <p>制品字节的读取通过 {@link ArtifactStorage} 接口,不直连文件系统(根 CLAUDE.md 第十一节)。实际的字节拷贝
 * 循环留在 interfaces 层的 Controller 里做——那是纯粹的 I/O 管道,不是业务逻辑,与
 * {@code ArtifactStorage} 类注释里"读取要直接灌给 HTTP 响应"的设计意图一致。
 */
@Service
public class SkillDownloadApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillDownloadApplicationService.class);

    private static final String SKILL_VERSION_NOT_FOUND_CODE = "SKILL_VERSION_NOT_FOUND";

    private final SkillArtifactRepository skillArtifactRepository;
    private final ArtifactStorage artifactStorage;
    private final DownloadDedupGuard downloadDedupGuard;

    public SkillDownloadApplicationService(
            SkillArtifactRepository skillArtifactRepository, ArtifactStorage artifactStorage,
            DownloadDedupGuard downloadDedupGuard) {
        this.skillArtifactRepository = skillArtifactRepository;
        this.artifactStorage = artifactStorage;
        this.downloadDedupGuard = downloadDedupGuard;
    }

    @Transactional(readOnly = true)
    public SkillArtifact resolveArtifact(long skillId, String version) {
        return skillArtifactRepository
                .findBySkillIdAndVersion(skillId, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        SKILL_VERSION_NOT_FOUND_CODE, "skill 版本不存在, skillId=" + skillId + ", version=" + version));
    }

    public InputStream openArtifact(String artifactKey) {
        return artifactStorage.openRead(artifactKey);
    }

    public long sizeOf(String artifactKey) {
        return artifactStorage.sizeOf(artifactKey);
    }

    /**
     * 计入 {@code skill.download_count},受 10 分钟时间窗去重保护(业务建模文档 5.12)。调用方(Controller)
     * 负责判断"这次响应是否真的写出了完整字节、是否是 HEAD/Range 请求"——那是 HTTP 语义判断,不下沉到这里。
     */
    @Transactional
    public void recordDownload(long skillId, long skillVersionId, String clientIp) {
        if (!downloadDedupGuard.shouldCount(clientIp, skillVersionId, Instant.now())) {
            log.debug(
                    "下载计数命中时间窗去重,跳过, skillId={}, skillVersionId={}",
                    skillId,
                    skillVersionId);
            return;
        }
        skillArtifactRepository.incrementDownloadCount(skillId);
    }
}
