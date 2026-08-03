package com.landray.skillhub.domain.repository;

import java.util.List;

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
}
