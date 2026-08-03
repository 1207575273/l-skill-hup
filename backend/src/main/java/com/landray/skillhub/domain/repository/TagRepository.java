package com.landray.skillhub.domain.repository;

import java.util.Collection;
import java.util.List;

import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagWithSkillCount;

/** tag 的仓储接口。只有接口,纯 Java,实现在 {@code infrastructure.persistence}。 */
public interface TagRepository {

    /**
     * 词条列表,附带各词条下可见(PUBLISHED)skill 的数量,实时 GROUP BY 算出。
     *
     * @param kindFilter {@code null} 表示不筛,返回 CURATED 与 FREE 全部
     */
    List<TagWithSkillCount> findAll(TagKind kindFilter);

    /** 按 id 批量查询,用于校验上传/更新请求里给出的 CURATED 标签 id 是否存在、kind 是否真的是 CURATED。 */
    List<Tag> findByIds(Collection<Long> ids);

    /**
     * 按名称查找 FREE 词条,不存在则创建。并发下两人同时打同一个新标签,靠 {@code uk_tag_name} 仲裁——命中
     * 冲突时捕获后重查已有行返回,对用户无感(业务建模文档 5.14)。
     */
    Tag findOrCreateFreeTag(String name, long createdByUserId);

    /**
     * 仅当该 FREE 标签此刻已无任何 {@code skill_tag} 引用时才删除,单条语句原子完成,不做先查后删
     * (业务建模文档 5.7:替换标签的同一事务内清理本次操作中失去全部引用的 FREE 标签)。对 CURATED 标签、
     * 仍有引用的 FREE 标签调用本方法都是安全的空操作。
     */
    void deleteFreeTagIfOrphaned(long tagId);
}
