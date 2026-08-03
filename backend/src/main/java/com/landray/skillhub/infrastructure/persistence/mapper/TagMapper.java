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

    /** 见 {@code AppUserMapper} 类注释:{@code useGeneratedKeys} 需要把生成的 id 写回参数 Map。 */
    int insertFree(Map<String, Object> params);

    /**
     * 单条语句原子完成"仅当这个 FREE 标签此刻已无任何 skill_tag 引用"的判断与删除,不做先查后删——避免两步之间
     * 出现别的请求刚好又给它打了一次标的竞态。CURATED 标签永不在这条语句的删除范围内(业务建模文档 5.7:
     * "CURATED 不动,它由 ADMIN 治理")。
     *
     * @return 实际删除的行数(0 或 1),调用方不需要关心具体数值,只用于测试断言
     */
    int deleteFreeTagIfOrphaned(@Param("tagId") long tagId);
}
