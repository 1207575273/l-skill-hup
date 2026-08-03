package com.landray.skillhub.infrastructure.persistence;

import java.util.List;

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
