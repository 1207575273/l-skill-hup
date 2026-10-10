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

    /**
     * 制品字节数。
     *
     * <p><b>当前没有调用方</b>:下载接口的 Content-Length 改从 {@code SkillArtifact.sizeBytes} 取,省掉一次
     * 对存储的往返(换对象存储后那是一次跨境请求)。保留这个方法而不是删掉,是因为它背后的
     * {@code ArtifactStorage.sizeOf} 是存储端口的一项能力,巡检"库里记的 size_bytes 与存储里实际对象是否一致"
     * 需要它 —— 那正是上面那个改动唯一放弃的东西(改之后长度信的是库,不是存储)。真要彻底不做这种校对,
     * 这里连同端口方法一起删,不要只留一个没人用的壳。
     */
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
