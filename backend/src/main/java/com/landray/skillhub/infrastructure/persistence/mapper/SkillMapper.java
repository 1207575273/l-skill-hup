package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.infrastructure.persistence.po.SkillDetailRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillDigestMatchRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillListRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillTagRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillVersionRow;

/**
 * skill 只读查询。SQL 全部在 {@code resources/mapper/SkillMapper.xml}——本任务的每一条都带条件/join/动态拼接,
 * 按 backend/CLAUDE.md 第三节规约,不允许用注解 SQL。
 *
 * <p>参数刻意都是原始类型 / 集合,不是把整个 {@code SkillSearchCriteria} 领域对象传进来:MyBatis 的 OGNL 对
 * record 的属性访问支持仍在演进中(与传统 JavaBean getter 相比历史更短、验证更少),传扁平参数是更保守、可预期性
 * 更高的写法,领域对象到 SQL 参数的转换在 {@code SkillRepositoryImpl} 里做。
 *
 * <p>{@code sortColumn} 通过 {@code ${}} 而不是 {@code #{}} 拼进 ORDER BY——SQL 标识符(列名)不能用绑定变量
 * 参数化,这是 MyBatis 动态 ORDER BY 的标准写法。**安全前提**:调用方必须只传枚举
 * {@link com.landray.skillhub.domain.model.SkillSortOption} 映射出的固定白名单字符串(见
 * {@code SkillRepositoryImpl#resolveSortColumn}),不能是用户输入的原始字符串——这不是 backend/CLAUDE.md
 * 第三节禁止的"字符串拼接 SQL"(那条防的是应用层拼装完整 SQL 语句),而是 MyBatis 提供的、专门用于参数化标识符
 * 的机制。
 */
@Mapper
public interface SkillMapper {

    List<SkillListRow> selectListPage(
            @Param("source") String source,
            @Param("tagIds") List<Long> tagIds,
            @Param("tagCount") int tagCount,
            @Param("certifiedOnly") boolean certifiedOnly,
            @Param("keywordPattern") String keywordPattern,
            @Param("ownerUserId") Long ownerUserId,
            @Param("statuses") List<String> statuses,
            @Param("sortColumn") String sortColumn,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countList(
            @Param("source") String source,
            @Param("tagIds") List<Long> tagIds,
            @Param("tagCount") int tagCount,
            @Param("certifiedOnly") boolean certifiedOnly,
            @Param("keywordPattern") String keywordPattern,
            @Param("ownerUserId") Long ownerUserId,
            @Param("statuses") List<String> statuses);

    List<SkillTagRow> selectTagsBySkillIds(@Param("skillIds") List<Long> skillIds);

    SkillDetailRow selectDetailById(@Param("skillId") long skillId);

    int countById(@Param("skillId") long skillId);

    List<SkillVersionRow> selectVersionsBySkillId(@Param("skillId") long skillId);

    /** 见 {@code SkillRepository#findVersionBySkillIdAndVersion}:联合条件挡住"用 A 的 id 配 B 的版本号"。 */
    SkillVersionRow selectVersionBySkillIdAndVersion(
            @Param("skillId") long skillId, @Param("version") String version);

    /** 见 {@code SkillRepository#findVersionArtifactKey} 类注释:{@code skillId}/{@code versionId} 联合条件。 */
    String selectVersionArtifactKey(@Param("skillId") long skillId, @Param("versionId") long versionId);

    /** 复用 {@code selectDetailById} 同一份 resultMap,只是过滤条件换成 name(precheck 重名检测,业务建模文档 6.2)。 */
    SkillDetailRow selectDetailByName(@Param("name") String name);

    SkillDigestMatchRow selectVersionByContentDigest(@Param("contentDigest") String contentDigest);

    String selectLatestVersionString(@Param("skillId") long skillId);

    /** 见 {@code AppUserMapper} 类注释:{@code useGeneratedKeys} 需要把生成的 id 写回参数 Map,record 做不到。 */
    int insertSkill(Map<String, Object> params);

    int insertVersion(Map<String, Object> params);

    int updateLatestVersionIfGreater(
            @Param("skillId") long skillId,
            @Param("versionId") long versionId,
            @Param("version") String version,
            @Param("description") String description,
            @Param("cardExcerpt") String cardExcerpt);

    List<Long> selectTagIdsBySkillId(@Param("skillId") long skillId);

    void deleteSkillTags(@Param("skillId") long skillId);

    void insertSkillTags(@Param("skillId") long skillId, @Param("tagIds") List<Long> tagIds);

    /**
     * 认领:仅当当前归属仍是 {@code systemUserId}(无主)时才改。
     *
     * @return 受影响行数;0 表示已被别人抢先认领(见 XML 里对比较并交换的说明)
     */
    int claimOwnership(
            @Param("skillId") long skillId,
            @Param("newOwnerUserId") long newOwnerUserId,
            @Param("systemUserId") long systemUserId);

    void updateMetadata(
            @Param("skillId") long skillId, @Param("displayName") String displayName, @Param("source") String source);

    void updateStatus(@Param("skillId") long skillId, @Param("status") String status);

    /** {@code certified_at} 由 SQL 侧的 {@code now()} 给,不从应用侧传时间——理由见 {@code SkillRepository}。 */
    void updateCertification(
            @Param("versionId") long versionId,
            @Param("certification") String certification,
            @Param("operatorUserId") long operatorUserId,
            @Param("note") String note);

    List<String> selectVersionArtifactKeys(@Param("skillId") long skillId);

    void deleteVersionsBySkillId(@Param("skillId") long skillId);

    void deleteReactionsBySkillId(@Param("skillId") long skillId);

    void deleteFavoritesBySkillId(@Param("skillId") long skillId);

    void deleteCommentsBySkillId(@Param("skillId") long skillId);

    void deleteSkillById(@Param("skillId") long skillId);

    /** 增量调整 skill.comment_count(5.15:追加/移除语义走增量,不重算)。 */
    void adjustCommentCount(@Param("skillId") long skillId, @Param("delta") int delta);
}
