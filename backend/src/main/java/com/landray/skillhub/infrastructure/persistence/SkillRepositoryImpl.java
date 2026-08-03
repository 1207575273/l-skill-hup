package com.landray.skillhub.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.LikePatternEscaper;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.SkillMapper;
import com.landray.skillhub.infrastructure.persistence.po.SkillDetailRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillListRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillTagRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillVersionRow;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@link SkillRepository} 的 MyBatis 实现。
 *
 * <p>标签装配统一走"先取一页 skill(不带标签)-> 按 skillId 批量取标签 -> 在内存里分组挂回去"这条路径,
 * 无论是列表还是详情都一样——理由见 {@code SkillMapper.xml} 顶部注释:一旦在主查询里直接 JOIN
 * {@code skill_tag}/{@code tag},一个挂了 3 个标签的 skill 就会展开成 3 行,分页的 LIMIT/OFFSET 语义直接错掉。
 */
@Repository
public class SkillRepositoryImpl implements SkillRepository {

    private final SkillMapper skillMapper;

    public SkillRepositoryImpl(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    @Override
    public PageResult<Skill> search(SkillSearchCriteria criteria) {
        String sourceValue = criteria.source() == null ? null : criteria.source().name();
        List<Long> tagIds =
                criteria.tagIds() == null || criteria.tagIds().isEmpty() ? null : criteria.tagIds();
        int tagCount = tagIds == null ? 0 : tagIds.size();
        String keywordPattern = LikePatternEscaper.toLikePattern(criteria.keyword());
        String sortColumn = resolveSortColumn(criteria.sort());

        List<SkillListRow> rows = skillMapper.selectListPage(
                sourceValue,
                tagIds,
                tagCount,
                criteria.certifiedOnly(),
                keywordPattern,
                criteria.ownerUserId(),
                sortColumn,
                criteria.size(),
                criteria.offset());
        long total = skillMapper.countList(
                sourceValue, tagIds, tagCount, criteria.certifiedOnly(), keywordPattern, criteria.ownerUserId());

        List<Long> skillIds = rows.stream().map(SkillListRow::id).toList();
        Map<Long, List<Tag>> tagsBySkillId = fetchTagsBySkillIds(skillIds);

        List<Skill> items = rows.stream()
                .map(row -> toDomain(row, tagsBySkillId.getOrDefault(row.id(), List.of())))
                .toList();

        return new PageResult<>(items, total, criteria.page(), criteria.size());
    }

    @Override
    public Optional<Skill> findDetailById(long skillId) {
        SkillDetailRow row = skillMapper.selectDetailById(skillId);
        if (row == null) {
            return Optional.empty();
        }
        List<Tag> tags = fetchTagsBySkillIds(List.of(skillId)).getOrDefault(skillId, List.of());
        return Optional.of(toDomain(row, tags));
    }

    @Override
    public boolean existsById(long skillId) {
        return skillMapper.countById(skillId) > 0;
    }

    @Override
    public List<SkillVersion> findVersionsBySkillId(long skillId) {
        return skillMapper.selectVersionsBySkillId(skillId).stream()
                .map(SkillRepositoryImpl::toDomain)
                .toList();
    }

    private Map<Long, List<Tag>> fetchTagsBySkillIds(List<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        List<SkillTagRow> tagRows = skillMapper.selectTagsBySkillIds(skillIds);
        Map<Long, List<Tag>> result = new LinkedHashMap<>();
        for (SkillTagRow row : tagRows) {
            result.computeIfAbsent(row.skillId(), key -> new ArrayList<>()).add(toDomain(row));
        }
        return result;
    }

    /**
     * DOWNLOADS/LIKES/LATEST -> 固定的 SQL 列名白名单,供 {@code ORDER BY ${sortColumn}} 使用。
     *
     * <p>只允许从这个 switch 产出的固定字符串流入 {@code ${}},不接受任何外部输入直接拼入——枚举的封闭性
     * 保证了这里不存在 SQL 注入面,具体理由见 {@code SkillMapper} 接口的类注释。
     */
    private String resolveSortColumn(SkillSortOption sort) {
        return switch (sort) {
            case DOWNLOADS -> "s.download_count";
            case LIKES -> "s.like_count";
            case LATEST -> "s.updated_at";
        };
    }

    private static Skill toDomain(SkillListRow row, List<Tag> tags) {
        SkillVersion latestVersion = row.latestVersionId() == null
                ? null
                : new SkillVersion(
                        row.latestVersionId(),
                        row.id(),
                        row.latestVersionVersion(),
                        0L,
                        0,
                        List.of(),
                        null,
                        null,
                        null,
                        row.latestVersionCertification(),
                        null,
                        null,
                        null,
                        null,
                        null);
        return new Skill(
                row.id(),
                new SkillName(row.name()),
                row.displayName(),
                null,
                null,
                new SkillOwner(row.ownerUserId(), row.ownerDisplayName(), row.ownerEmployeeNo()),
                row.cardExcerpt(),
                SkillStatus.PUBLISHED,
                row.downloadCount(),
                row.likeCount(),
                0,
                row.favoriteCount(),
                0,
                null,
                row.updatedAt(),
                latestVersion,
                tags);
    }

    private static Skill toDomain(SkillDetailRow row, List<Tag> tags) {
        SkillVersion latestVersion = row.latestVersionId() == null
                ? null
                : new SkillVersion(
                        row.latestVersionId(),
                        row.id(),
                        row.latestVersionVersion(),
                        row.latestVersionSizeBytes() == null ? 0L : row.latestVersionSizeBytes(),
                        row.latestVersionFileCount() == null ? 0 : row.latestVersionFileCount(),
                        row.latestVersionAllowedTools() == null ? List.of() : row.latestVersionAllowedTools(),
                        row.latestVersionSkillMdContent(),
                        row.latestVersionReadmeContent(),
                        row.latestVersionChangelog(),
                        row.latestVersionCertification(),
                        row.latestVersionCertifiedByUserId(),
                        row.latestVersionCertifiedAt(),
                        row.latestVersionCertifyNote(),
                        row.latestVersionCreatedByUserId(),
                        row.latestVersionCreatedAt());
        return new Skill(
                row.id(),
                new SkillName(row.name()),
                row.displayName(),
                row.description(),
                row.source(),
                new SkillOwner(row.ownerUserId(), row.ownerDisplayName(), row.ownerEmployeeNo()),
                row.cardExcerpt(),
                row.status(),
                row.downloadCount(),
                row.likeCount(),
                row.dislikeCount(),
                row.favoriteCount(),
                row.commentCount(),
                row.createdAt(),
                row.updatedAt(),
                latestVersion,
                tags);
    }

    private static Tag toDomain(SkillTagRow row) {
        return new Tag(
                row.tagId(),
                row.tagName(),
                row.tagKind(),
                row.tagDescription(),
                row.tagSortOrder(),
                row.tagCreatedByUserId(),
                row.tagCreatedAt(),
                row.tagUpdatedAt());
    }

    private static SkillVersion toDomain(SkillVersionRow row) {
        return new SkillVersion(
                row.id(),
                null,
                row.version(),
                row.sizeBytes(),
                row.fileCount(),
                List.of(),
                null,
                null,
                null,
                row.certification(),
                row.certifiedByUserId(),
                row.certifiedAt(),
                row.certifyNote(),
                row.createdByUserId(),
                row.createdAt());
    }
}
