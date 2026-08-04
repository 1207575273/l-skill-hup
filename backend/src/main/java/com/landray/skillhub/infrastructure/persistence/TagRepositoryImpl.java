package com.landray.skillhub.infrastructure.persistence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagName;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.repository.TagRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.TagMapper;
import com.landray.skillhub.infrastructure.persistence.po.TagRow;
import com.landray.skillhub.shared.exception.ResourceConflictException;

/** {@link TagRepository} 的 MyBatis 实现。 */
@Repository
public class TagRepositoryImpl implements TagRepository {

    private static final String TAG_NAME_DUPLICATED_CODE = "TAG_NAME_DUPLICATED";

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
    public Optional<Tag> findById(long tagId) {
        return Optional.ofNullable(tagMapper.selectById(tagId));
    }

    @Override
    public Optional<Tag> findByName(String name) {
        return Optional.ofNullable(tagMapper.selectByName(name));
    }

    @Override
    public List<TagWithSkillCount> suggest(String keyword, TagKind kindFilter, int limit) {
        return tagMapper.selectSuggestions(keyword, kindFilter, limit).stream()
                .map(TagRepositoryImpl::toDomain)
                .toList();
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

    /**
     * 与 {@link #findOrCreateFreeTag} 相反:撞名直接 409。ADMIN 显式建一个词条,静默返回一个早就存在的同名
     * 词条会让他以为自己刚设的 description 与 sortOrder 生效了,而实际上一个字都没写进去。
     */
    @Override
    public Tag create(TagName name, TagKind kind, String description, int sortOrder, long createdByUserId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name.value());
        params.put("kind", kind);
        params.put("description", description);
        params.put("sortOrder", sortOrder);
        params.put("createdByUserId", createdByUserId);
        try {
            tagMapper.insertTag(params);
        } catch (DuplicateKeyException ignored) {
            throw nameConflict(name.value());
        }
        Long generatedId = (Long) params.get("id");
        return new Tag(generatedId, name.value(), kind, description, sortOrder, createdByUserId, null, null);
    }

    @Override
    public void update(long tagId, TagName name, TagKind kind, String description, int sortOrder) {
        try {
            tagMapper.updateTag(tagId, name.value(), kind, description, sortOrder);
        } catch (DuplicateKeyException ignored) {
            throw nameConflict(name.value());
        }
    }

    @Override
    public long countReferences(long tagId) {
        return tagMapper.countReferences(tagId);
    }

    @Override
    public List<String> findSkillNamesReferencing(long tagId, int sampleLimit) {
        return tagMapper.selectSkillNamesReferencing(tagId, sampleLimit);
    }

    @Override
    public List<String> findSkillNamesAtCuratedLimit(long tagId, int curatedLimit, int sampleLimit) {
        return tagMapper.selectSkillNamesAtCuratedLimit(tagId, curatedLimit, sampleLimit);
    }

    @Override
    public void migrateReferences(long fromTagId, long toTagId) {
        tagMapper.insertMigratedReferences(fromTagId, toTagId);
        tagMapper.deleteReferences(fromTagId);
    }

    @Override
    public void deleteReferences(long tagId) {
        tagMapper.deleteReferences(tagId);
    }

    @Override
    public void deleteTag(long tagId) {
        tagMapper.deleteTag(tagId);
    }

    @Override
    public void deleteFreeTagIfOrphaned(long tagId) {
        tagMapper.deleteFreeTagIfOrphaned(tagId);
    }

    /**
     * {@code uk_tag_name} 是最终防线。application 层已经先查过一次重名,但那是"先查后写",两次之间另一个
     * ADMIN 完全可能插进来——所以这里必须再接一道,不能假设前面查过就不会撞。
     *
     * <p>不串 {@code DuplicateKeyException} 的 cause:这是一条 409,{@code GlobalExceptionHandler} 按 4xx
     * 走 WARN 且不打栈(backend/CLAUDE.md 6.2),串上去的栈没有任何人会看到,而"已存在同名词条, name=xxx"
     * 本身已经把发生了什么说完了。
     */
    private static ResourceConflictException nameConflict(String name) {
        return new ResourceConflictException(
                TAG_NAME_DUPLICATED_CODE, "已存在同名词条, name=" + name, Map.of("name", name));
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
