package com.landray.skillhub.domain.repository;

import java.util.List;
import java.util.Optional;

import com.landray.skillhub.domain.model.SkillArtifact;

/**
 * 面向"下载与安装脚本渲染"的窄查询接口,join {@code skill} 与 {@code skill_version}。
 *
 * <p>刻意不复用 {@code SkillRepository}——那个接口服务于 skill 详情/列表读侧(由另一条并行任务线维护,
 * 详见任务边界说明),这里只取安装链路需要的字段,两条查询各自独立演进,互不影响。
 */
public interface SkillArtifactRepository {

    /** {@code GET /skills/{id}/versions/{version}/download} 用:按 skillId + 版本号定位一个制品。 */
    Optional<SkillArtifact> findBySkillIdAndVersion(long skillId, String version);

    /** 安装单渲染 / 生成时校验用:按一批 {@code skill_version.id} 批量取。 */
    List<SkillArtifact> findByVersionIds(List<Long> versionIds);

    /** 下载计数,数据库原子自增(业务建模文档 5.12),不读不算。 */
    void incrementDownloadCount(long skillId);
}
