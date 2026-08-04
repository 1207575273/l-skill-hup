package com.landray.skillhub.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.exception.TagDeleteRequiresMigrationException;
import com.landray.skillhub.domain.exception.TagKindConversionException;
import com.landray.skillhub.domain.exception.TagMigrationTargetInvalidException;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagName;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.repository.TagRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

/**
 * 标签治理:ADMIN 建 / 改 / 删词条,以及 {@code kind} 转换(业务建模文档 5.7、6.2)。
 *
 * <p>与 {@link TagApplicationService}(读侧)分开:那边是任何人都能调的列表查询,这边每个方法都要求 ADMIN
 * 且都在写库。合在一起会让"这个类要不要鉴权"变成一句"看方法",而这正是权限漏判最爱藏身的地方。
 *
 * <p><b>本类承载的规则数据库一条都拦不住。</b>每个 skill 至多 3 个 CURATED、迁移目标的 kind 要一致、
 * 转换前要让 ADMIN 看见影响范围——全是跨行跨表的判断,没有约束能表达。所以每一条都必须有测试
 * (业务建模文档 4.7 明写)。
 */
@Service
public class TagGovernanceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TagGovernanceApplicationService.class);

    /** 受影响 skill 清单的样本上限。全量返回没有意义——ADMIN 要的是"影响有多大",不是把几百个名字读完。 */
    private static final int AFFECTED_SAMPLE_LIMIT = 20;

    private static final int DEFAULT_SUGGEST_LIMIT = 10;
    private static final int MAX_SUGGEST_LIMIT = 50;

    private final TagRepository tagRepository;
    private final AuditLog auditLog;

    public TagGovernanceApplicationService(TagRepository tagRepository, AuditLog auditLog) {
        this.tagRepository = tagRepository;
        this.auditLog = auditLog;
    }

    /**
     * 新建 CURATED 词条。
     *
     * <p><b>kind 不作为参数</b>:接口契约(6.2)写的就是"新建 CURATED 词条"。FREE 词条由用户打标签时自动产生,
     * 给 ADMIN 开一个手工建 FREE 的口子,建出来的当场就是零引用孤儿,下一次有人改标签就被清理掉——一个只会
     * 制造困惑的功能。
     */
    @Transactional
    public Tag createCuratedTag(String rawName, String description, Integer sortOrder, AuthenticatedUser admin) {
        TagName name = new TagName(rawName);
        requireNameAvailable(name, null);

        Tag created = tagRepository.create(
                name, TagKind.CURATED, normalizeDescription(description), orZero(sortOrder), admin.userId());

        auditLog.info(
                "标签治理, event={}, tagId={}, name={}, kind={}, operator={}",
                "tag_created",
                created.id(),
                created.name(),
                TagKind.CURATED,
                admin.userId());
        return created;
    }

    /**
     * 改名 / 描述 / 排序 / kind 转换。
     *
     * @param confirmed ADMIN 是否已经看过 {@code CURATED -> FREE} 的受影响清单并确认。只对那一个方向有意义,
     *     其余情况忽略——不能让它变成一个"勾上就什么校验都不做"的万能开关。
     */
    @Transactional
    public Tag updateTag(
            long tagId,
            String rawName,
            String kindParam,
            String description,
            Integer sortOrder,
            boolean confirmed,
            AuthenticatedUser admin) {
        Tag existing = requireTag(tagId);
        TagName name = new TagName(rawName);
        requireNameAvailable(name, tagId);

        TagKind targetKind = kindParam == null || kindParam.isBlank() ? existing.kind() : TagKind.fromParam(kindParam);
        if (targetKind != existing.kind()) {
            requireKindConversionAllowed(existing, targetKind, confirmed);
        }

        // FREE 词条的 description 与 sort_order 恒为空(业务建模文档 4.6:两者"仅 CURATED 用")。转成 FREE 时
        // 主动清掉,而不是把 CURATED 时代的描述留在库里——留着的话再转回 CURATED 会诈尸出一段没人记得的旧文案。
        String finalDescription = targetKind == TagKind.CURATED ? normalizeDescription(description) : null;
        int finalSortOrder = targetKind == TagKind.CURATED ? orZero(sortOrder) : 0;

        tagRepository.update(tagId, name, targetKind, finalDescription, finalSortOrder);

        if (targetKind != existing.kind()) {
            // kind 转换直接改变一批 skill 的可发现性,值得一条能被巡检看到的记录(backend/CLAUDE.md 6.2)。
            log.warn(
                    "标签 kind 已转换, tagId={}, name={}, from={}, to={}, operator={}",
                    tagId,
                    name.value(),
                    existing.kind(),
                    targetKind,
                    admin.userId());
        }
        auditLog.info(
                "标签治理, event={}, tagId={}, name={}, kind={}, previousKind={}, operator={}",
                "tag_updated",
                tagId,
                name.value(),
                targetKind,
                existing.kind(),
                admin.userId());
        return new Tag(
                tagId,
                name.value(),
                targetKind,
                finalDescription,
                finalSortOrder,
                existing.createdByUserId(),
                existing.createdAt(),
                null);
    }

    /**
     * 删除词条。{@code migrateToTagId} 非空时把引用整体迁过去,否则直接断开引用。
     *
     * <p>CURATED 且仍有引用时必须给迁移目标,理由见 {@link TagDeleteRequiresMigrationException}。
     */
    @Transactional
    public void deleteTag(long tagId, Long migrateToTagId, AuthenticatedUser admin) {
        Tag existing = requireTag(tagId);
        long referenceCount = tagRepository.countReferences(tagId);

        if (migrateToTagId != null) {
            Tag target = resolveMigrationTarget(existing, migrateToTagId);
            tagRepository.migrateReferences(tagId, target.id());
        } else {
            if (existing.kind() == TagKind.CURATED && referenceCount > 0) {
                throw new TagDeleteRequiresMigrationException(
                        referenceCount, tagRepository.findSkillNamesReferencing(tagId, AFFECTED_SAMPLE_LIMIT));
            }
            tagRepository.deleteReferences(tagId);
        }
        tagRepository.deleteTag(tagId);

        log.warn(
                "标签已被删除, tagId={}, name={}, kind={}, referenceCount={}, migrateToTagId={}, operator={}",
                tagId,
                existing.name(),
                existing.kind(),
                referenceCount,
                migrateToTagId,
                admin.userId());
        auditLog.info(
                "标签治理, event={}, tagId={}, name={}, kind={}, referenceCount={}, migrateToTagId={}, operator={}",
                "tag_deleted",
                tagId,
                existing.name(),
                existing.kind(),
                referenceCount,
                migrateToTagId,
                admin.userId());
    }

    /**
     * 输入联想(业务建模文档 5.7:防标签爆炸的主力)。
     *
     * <p>空关键词返回空列表而不是报错:输入框刚被清空时前端会打过来一次,那不是错误。同理关键词超长也只是
     * 匹配不到而已,不必拦。
     */
    @Transactional(readOnly = true)
    public List<TagWithSkillCount> suggest(String keyword, String kindParam, Integer limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        TagKind kindFilter = kindParam == null || kindParam.isBlank() ? null : TagKind.fromParam(kindParam);
        return tagRepository.suggest(keyword.trim().replaceAll("\\s+", " "), kindFilter, clampSuggestLimit(limit));
    }

    private void requireKindConversionAllowed(Tag existing, TagKind targetKind, boolean confirmed) {
        if (targetKind == TagKind.CURATED) {
            // FREE -> CURATED:硬拒绝。数据库拦不住"某个 skill 变成 4 个分类",只能在这里挡。
            int curatedLimit = TagKind.CURATED.maxPerSkill();
            List<String> overflowing =
                    tagRepository.findSkillNamesAtCuratedLimit(existing.id(), curatedLimit, AFFECTED_SAMPLE_LIMIT);
            if (!overflowing.isEmpty()) {
                throw TagKindConversionException.curatedLimitWouldOverflow(curatedLimit, overflowing);
            }
            return;
        }

        // CURATED -> FREE:告知而非拒绝。零引用时没有任何人受影响,不必打扰 ADMIN。
        if (confirmed) {
            return;
        }
        long affectedCount = tagRepository.countReferences(existing.id());
        if (affectedCount > 0) {
            throw TagKindConversionException.needsConfirmation(
                    affectedCount, tagRepository.findSkillNamesReferencing(existing.id(), AFFECTED_SAMPLE_LIMIT));
        }
    }

    private Tag resolveMigrationTarget(Tag source, long migrateToTagId) {
        if (Objects.equals(migrateToTagId, source.id())) {
            throw new TagMigrationTargetInvalidException("迁移目标不能是被删除的词条自己, tagId=" + migrateToTagId);
        }
        Tag target = tagRepository
                .findById(migrateToTagId)
                .orElseThrow(() -> new TagMigrationTargetInvalidException("迁移目标不存在, tagId=" + migrateToTagId));
        if (target.kind() != source.kind()) {
            throw new TagMigrationTargetInvalidException(
                    "迁移目标的类型必须与被删词条一致, from=" + source.kind() + ", to=" + target.kind());
        }
        return target;
    }

    private Tag requireTag(long tagId) {
        return tagRepository
                .findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("TAG_NOT_FOUND", "标签不存在, tagId=" + tagId));
    }

    /**
     * 先查一次重名,只为把错误消息说清楚。{@code uk_tag_name} 才是最终防线——两次请求之间另一个 ADMIN
     * 完全可能插进来,仓储层还有一道兜底(见 {@code TagRepositoryImpl#create})。
     *
     * @param selfTagId 改名场景传自己的 id,名字没变时不算冲突;新建场景传 {@code null}
     */
    private void requireNameAvailable(TagName name, Long selfTagId) {
        Optional<Tag> sameName = tagRepository.findByName(name.value());
        if (sameName.isEmpty()) {
            return;
        }
        Long conflictingId = sameName.get().id();
        if (selfTagId != null && conflictingId != null && conflictingId.equals(selfTagId)) {
            return;
        }
        throw new ResourceConflictException("TAG_NAME_DUPLICATED", "已存在同名词条, name=" + name.value());
    }

    /** 空白描述统一存 null,避免库里同时出现 null 和 "" 两种"没有描述"。 */
    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private static int orZero(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private static int clampSuggestLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_SUGGEST_LIMIT;
        }
        return Math.min(limit, MAX_SUGGEST_LIMIT);
    }
}
