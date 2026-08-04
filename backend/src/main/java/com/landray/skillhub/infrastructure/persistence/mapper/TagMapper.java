package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.infrastructure.persistence.po.TagRow;

/** tag 查询与写入。SQL 在 {@code resources/mapper/TagMapper.xml}(带 join 与可选筛选条件)。 */
@Mapper
public interface TagMapper {

    List<TagRow> selectAll(@Param("kind") TagKind kind);

    /** 直接映射到领域模型 {@link Tag}——这条查询的列与 Tag 的字段 1:1 对应,不需要额外的 PO(backend/CLAUDE.md 第二节)。 */
    List<Tag> selectByIds(@Param("ids") Collection<Long> ids);

    Tag selectByName(@Param("name") String name);

    Tag selectById(@Param("tagId") long tagId);

    /**
     * 输入联想。{@code keyword} 由调用方归一化并转小写后传入,SQL 侧对 {@code t.name} 也做 {@code lower()}
     * 再匹配,保证大小写不敏感。
     */
    List<TagRow> selectSuggestions(
            @Param("keyword") String keyword, @Param("kind") TagKind kind, @Param("limit") int limit);

    /** 见 {@code AppUserMapper} 类注释:{@code useGeneratedKeys} 需要把生成的 id 写回参数 Map。 */
    int insertFree(Map<String, Object> params);

    /** 同 {@link #insertFree},走 Map 是为了取回自增 id。{@code kind} 由调用方给,不写死。 */
    int insertTag(Map<String, Object> params);

    int updateTag(
            @Param("tagId") long tagId,
            @Param("name") String name,
            @Param("kind") TagKind kind,
            @Param("description") String description,
            @Param("sortOrder") int sortOrder);

    /** 引用数含已下架 skill,理由见 {@code TagRepository#countReferences}。 */
    long countReferences(@Param("tagId") long tagId);

    List<String> selectSkillNamesReferencing(@Param("tagId") long tagId, @Param("limit") int limit);

    List<String> selectSkillNamesAtCuratedLimit(
            @Param("tagId") long tagId, @Param("curatedLimit") int curatedLimit, @Param("limit") int limit);

    /** 只插不存在的那部分,跳过已经挂着目标词条的 skill,避免撞 {@code uk_skill_tag}。 */
    int insertMigratedReferences(@Param("fromTagId") long fromTagId, @Param("toTagId") long toTagId);

    int deleteReferences(@Param("tagId") long tagId);

    int deleteTag(@Param("tagId") long tagId);

    /**
     * 单条语句原子完成"仅当这个 FREE 标签此刻已无任何 skill_tag 引用"的判断与删除,不做先查后删——避免两步之间
     * 出现别的请求刚好又给它打了一次标的竞态。CURATED 标签永不在这条语句的删除范围内(业务建模文档 5.7:
     * "CURATED 不动,它由 ADMIN 治理")。
     *
     * @return 实际删除的行数(0 或 1),调用方不需要关心具体数值,只用于测试断言
     */
    int deleteFreeTagIfOrphaned(@Param("tagId") long tagId);
}
