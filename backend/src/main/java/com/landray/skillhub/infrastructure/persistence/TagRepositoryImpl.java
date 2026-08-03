package com.landray.skillhub.infrastructure.persistence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.repository.TagRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.TagMapper;
import com.landray.skillhub.infrastructure.persistence.po.TagRow;

/** {@link TagRepository} 的 MyBatis 实现。 */
@Repository
public class TagRepositoryImpl implements TagRepository {

    private final TagMapper tagMapper;

    public TagRepositoryImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public List<TagWithSkillCount> findAll(TagKind kindFilter) {
        return tagMapper.selectAll(kindFilter).stream()
                .map(TagRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public List<Tag> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectByIds(ids);
    }

    @Override
    public Tag findOrCreateFreeTag(String name, long createdByUserId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("createdByUserId", createdByUserId);
        try {
            tagMapper.insertFree(params);
            Long generatedId = (Long) params.get("id");
            // 刚插入的行,createdAt/updatedAt 由数据库 DEFAULT now() 生成,这里不再多发一次查询去拿精确值——
            // 调用方(标签分配编排)只需要 id 与 kind 就能完成后续的 skill_tag 关联,不依赖时间戳。
            return new Tag(generatedId, name, TagKind.FREE, null, 0, createdByUserId, null, null);
        } catch (DuplicateKeyException e) {
            Tag existing = tagMapper.selectByName(name);
            if (existing == null) {
                // 刚捕获的正是这条 name 的唯一约束冲突,理论上此刻该行必然存在;走到这里说明约束名或数据状态
                // 出现了未预期的变化,不能吞掉,包一层异常保留原始信息方便排查。
                throw new IllegalStateException("uk_tag_name 冲突后重查未找到该标签, name=" + name, e);
            }
            return existing;
        }
    }

    @Override
    public void deleteFreeTagIfOrphaned(long tagId) {
        tagMapper.deleteFreeTagIfOrphaned(tagId);
    }

    private static TagWithSkillCount toDomain(TagRow row) {
        Tag tag = new Tag(
                row.id(),
                row.name(),
                row.kind(),
                row.description(),
                row.sortOrder(),
                row.createdByUserId(),
                row.createdAt(),
                row.updatedAt());
        return new TagWithSkillCount(tag, row.skillCount());
    }
}
