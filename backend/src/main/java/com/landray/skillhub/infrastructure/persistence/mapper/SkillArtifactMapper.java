package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.infrastructure.persistence.po.SkillArtifactRow;

/**
 * 面向下载与安装脚本渲染的窄查询,join {@code skill} 与 {@code skill_version}。SQL 在
 * {@code resources/mapper/SkillArtifactMapper.xml}。
 *
 * <p>这是安装链路<b>自己的</b> Mapper,不与 {@code SkillMapper} 共用——归属不同任务边界,详见任务交接说明。
 */
@Mapper
public interface SkillArtifactMapper {

    SkillArtifactRow selectBySkillIdAndVersion(@Param("skillId") long skillId, @Param("version") String version);

    List<SkillArtifactRow> selectByVersionIds(@Param("versionIds") List<Long> versionIds);

    /** {@code UPDATE skill SET download_count = download_count + 1}——数据库原子自增,不读不算(文档 5.12)。 */
    void incrementDownloadCount(@Param("skillId") long skillId);
}
