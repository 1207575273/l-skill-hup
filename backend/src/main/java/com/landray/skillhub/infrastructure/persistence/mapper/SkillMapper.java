package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.infrastructure.persistence.po.SkillDetailRow;
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
            @Param("sortColumn") String sortColumn,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countList(
            @Param("source") String source,
            @Param("tagIds") List<Long> tagIds,
            @Param("tagCount") int tagCount,
            @Param("certifiedOnly") boolean certifiedOnly,
            @Param("keywordPattern") String keywordPattern,
            @Param("ownerUserId") Long ownerUserId);

    List<SkillTagRow> selectTagsBySkillIds(@Param("skillIds") List<Long> skillIds);

    SkillDetailRow selectDetailById(@Param("skillId") long skillId);

    int countById(@Param("skillId") long skillId);

    List<SkillVersionRow> selectVersionsBySkillId(@Param("skillId") long skillId);
}
