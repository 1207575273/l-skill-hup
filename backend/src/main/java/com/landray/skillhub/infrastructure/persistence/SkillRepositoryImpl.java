package com.landray.skillhub.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.LikePatternEscaper;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.SkillMapper;
import com.landray.skillhub.infrastructure.persistence.po.SkillDetailRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillDigestMatchRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillListRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillTagRow;
import com.landray.skillhub.infrastructure.persistence.po.SkillVersionRow;
import com.landray.skillhub.shared.exception.ResourceConflictException;
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
        // 枚举转字符串在这一层做,与 sourceValue 同一模式:mapper 参数只用原始类型 / 集合(见 SkillMapper 类注释)。
        List<String> statusValues =
                criteria.statuses().stream().map(SkillStatus::name).toList();

        List<SkillListRow> rows = skillMapper.selectListPage(
                sourceValue,
                tagIds,
                tagCount,
                criteria.certifiedOnly(),
                keywordPattern,
                criteria.ownerUserId(),
                statusValues,
                sortColumn,
                criteria.size(),
                criteria.offset());
        long total = skillMapper.countList(
                sourceValue,
                tagIds,
                tagCount,
                criteria.certifiedOnly(),
                keywordPattern,
                criteria.ownerUserId(),
                statusValues);

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
    public Optional<Skill> findByName(String name) {
        SkillDetailRow row = skillMapper.selectDetailByName(name);
        if (row == null) {
            return Optional.empty();
        }
        List<Tag> tags = fetchTagsBySkillIds(List.of(row.id())).getOrDefault(row.id(), List.of());
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

    @Override
    public Optional<SkillVersion> findVersionBySkillIdAndVersion(long skillId, String version) {
        return Optional.ofNullable(skillMapper.selectVersionBySkillIdAndVersion(skillId, version))
                .map(SkillRepositoryImpl::toDomain);
    }

    @Override
    public Optional<String> findVersionArtifactKey(long skillId, long versionId) {
        return Optional.ofNullable(skillMapper.selectVersionArtifactKey(skillId, versionId));
    }

    @Override
    public Optional<SkillVersion> findVersionByContentDigest(String contentDigest) {
        SkillDigestMatchRow row = skillMapper.selectVersionByContentDigest(contentDigest);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new SkillVersion(
                row.id(), row.skillId(), row.version(), null, null, null, 0L, 0, List.of(), null, null, null, null,
                null, null, null, null, null));
    }

    @Override
    public Optional<String> findLatestVersionString(long skillId) {
        return Optional.ofNullable(skillMapper.selectLatestVersionString(skillId));
    }

    @Override
    public long insertSkill(
            SkillName name, String displayName, String description, SkillSource source, long ownerUserId,
            String cardExcerpt) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name.value());
        params.put("displayName", displayName);
        params.put("description", description);
        params.put("source", source.name());
        params.put("ownerUserId", ownerUserId);
        params.put("cardExcerpt", cardExcerpt);
        try {
            skillMapper.insertSkill(params);
        } catch (DuplicateKeyException e) {
            throw new ResourceConflictException(
                    "SKILL_NAME_DUPLICATED", "已存在同名 skill: " + name.value(), Map.of("name", name.value()));
        }
        return (Long) params.get("id");
    }

    @Override
    public long insertVersion(SkillVersion draft) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("skillId", draft.skillId());
        params.put("version", draft.version());
        params.put("artifactKey", draft.artifactKey());
        params.put("contentDigest", draft.contentDigest());
        params.put("sha256", draft.sha256());
        params.put("sizeBytes", draft.sizeBytes());
        params.put("fileCount", draft.fileCount());
        params.put("allowedTools", draft.allowedTools());
        params.put("skillMdContent", draft.skillMdContent());
        params.put("readmeContent", draft.readmeContent());
        params.put("certification", draft.certification().name());
        params.put("createdByUserId", draft.createdByUserId());
        try {
            skillMapper.insertVersion(params);
        } catch (DuplicateKeyException e) {
            throw new ResourceConflictException(
                    "SKILL_VERSION_DUPLICATED",
                    "该 skill 已存在版本 " + draft.version(),
                    Map.of("skillId", draft.skillId(), "version", draft.version()));
        }
        return (Long) params.get("id");
    }

    @Override
    public boolean updateLatestVersionIfGreater(
            long skillId, long versionId, String version, String description, String cardExcerpt) {
        int updated = skillMapper.updateLatestVersionIfGreater(skillId, versionId, version, description, cardExcerpt);
        return updated > 0;
    }

    @Override
    public List<Long> replaceTags(long skillId, List<Long> tagIds) {
        List<Long> oldTagIds = skillMapper.selectTagIdsBySkillId(skillId);
        skillMapper.deleteSkillTags(skillId);
        if (!tagIds.isEmpty()) {
            skillMapper.insertSkillTags(skillId, tagIds);
        }
        return oldTagIds;
    }

    @Override
    public void updateMetadata(long skillId, String displayName, SkillSource source) {
        skillMapper.updateMetadata(skillId, displayName, source.name());
    }

    @Override
    public void adjustCommentCount(long skillId, int delta) {
        skillMapper.adjustCommentCount(skillId, delta);
    }

    @Override
    public void updateStatus(long skillId, SkillStatus status) {
        skillMapper.updateStatus(skillId, status.name());
    }

    @Override
    public void updateCertification(
            long versionId, Certification certification, long operatorUserId, String note) {
        skillMapper.updateCertification(versionId, certification.name(), operatorUserId, note);
    }

    @Override
    public List<String> findVersionArtifactKeys(long skillId) {
        return skillMapper.selectVersionArtifactKeys(skillId);
    }

    @Override
    public void deleteSkillCascade(long skillId) {
        // 先删引用方再删被引用方。本项目没有物理外键(建模原则:外键关系靠程序维护),所以数据库不会因为顺序
        // 反了而报错 —— 正因为没有那道保险,顺序才必须在代码里写对:反过来删,中途失败会留下一批指向已消失
        // skill 的孤儿行,而且没有任何约束会提示。
        skillMapper.deleteVersionsBySkillId(skillId);
        skillMapper.deleteReactionsBySkillId(skillId);
        skillMapper.deleteFavoritesBySkillId(skillId);
        skillMapper.deleteCommentsBySkillId(skillId);
        skillMapper.deleteSkillById(skillId);
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
                        null,
                        null,
                        null,
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
                row.status(),
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
                        row.latestVersionArtifactKey(),
                        null,
                        null,
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
                null,
                null,
                null,
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
