package com.landray.skillhub.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 用例编排:读侧(列表、详情、版本列表)+ 写侧(改元数据、下架/恢复)。事务边界只在这一层
 * (backend/CLAUDE.md 第五节)。
 *
 * <p>page / size 的默认值与夹紧(size 上限 100)在这里做,不在 {@link SkillSearchCriteria} 的紧凑构造器里——
 * "把不合法的外部输入变成合法值"是解释请求的职责,{@code SkillSearchCriteria} 只负责"值一旦构造出来就合法"。
 *
 * <p>写侧三个方法(改元数据、下架、恢复)共用同一条权限规则——owner 或 ADMIN(业务建模文档 5.16),这是一条
 * <b>资源级</b>权限判断(取决于"这是谁的 skill"),{@code @RequireLogin} 只能确认"登录了没有",判断不了
 * "是不是这个 skill 的 owner",所以校验必须落在这一层而不是拦截器。
 */
@Service
public class SkillApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillApplicationService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MIN_PAGE = 1;
    private static final int MIN_SIZE = 1;

    private static final String SKILL_NOT_FOUND_CODE = "SKILL_NOT_FOUND";

    private final SkillRepository skillRepository;
    private final SkillTagAssignmentService tagAssignmentService;
    private final AuditLog auditLog;

    public SkillApplicationService(
            SkillRepository skillRepository, SkillTagAssignmentService tagAssignmentService, AuditLog auditLog) {
        this.skillRepository = skillRepository;
        this.tagAssignmentService = tagAssignmentService;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public PageResult<Skill> listSkills(
            String sourceParam,
            List<Long> tagIds,
            boolean certifiedOnly,
            String keyword,
            Long ownerUserId,
            String sortParam,
            Integer pageParam,
            Integer sizeParam) {
        SkillSource source = sourceParam == null || sourceParam.isBlank() ? null : SkillSource.fromParam(sourceParam);
        SkillSortOption sort = SkillSortOption.fromParam(sortParam);
        int page = normalizePage(pageParam);
        int size = normalizeSize(sizeParam);

        SkillSearchCriteria criteria =
                new SkillSearchCriteria(source, tagIds, certifiedOnly, keyword, ownerUserId, sort, page, size);
        log.debug(
                "查询 skill 列表, source={}, tagCount={}, certifiedOnly={}, sort={}, page={}, size={}",
                source,
                tagIds == null ? 0 : tagIds.size(),
                certifiedOnly,
                sort,
                page,
                size);
        return skillRepository.search(criteria);
    }

    @Transactional(readOnly = true)
    public Skill getSkillDetail(long skillId) {
        return skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
    }

    @Transactional(readOnly = true)
    public List<SkillVersion> listVersions(long skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw notFound(skillId);
        }
        return skillRepository.findVersionsBySkillId(skillId);
    }

    /**
     * 改标签 / 来源 / 展示名(owner / ADMIN),{@code name} 不在参数列表里——它不可改
     * (业务建模文档 5.16),从结构上就不给"漏传了校验"这种回归留空间。
     */
    @Transactional
    public Skill updateMetadata(
            long skillId,
            String displayName,
            String sourceParam,
            List<Long> curatedTagIds,
            List<String> freeTagNames,
            AuthenticatedUser actingUser) {
        Skill skill = skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
        requireOwnerOrAdmin(skill, actingUser);

        SkillSource source = SkillSource.fromParam(sourceParam);
        List<Long> tagIds = tagAssignmentService.resolveTagIds(curatedTagIds, freeTagNames, actingUser.userId());

        skillRepository.updateMetadata(skillId, displayName, source);
        tagAssignmentService.replaceSkillTags(skillId, tagIds);

        log.info(
                "skill 元数据已更新, skillId={}, displayName={}, source={}, operator={}",
                skillId,
                displayName,
                source,
                actingUser.userId());
        return requireSkill(skillId);
    }

    /** 下架(owner / ADMIN),只改 status,不动版本、评论、表态、收藏(业务建模文档 5.15)。 */
    @Transactional
    public Skill unlist(long skillId, AuthenticatedUser actingUser) {
        return changeStatus(skillId, SkillStatus.UNLISTED, actingUser);
    }

    /** 恢复(owner / ADMIN)。 */
    @Transactional
    public Skill relist(long skillId, AuthenticatedUser actingUser) {
        return changeStatus(skillId, SkillStatus.PUBLISHED, actingUser);
    }

    private Skill changeStatus(long skillId, SkillStatus status, AuthenticatedUser actingUser) {
        Skill skill = skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
        requireOwnerOrAdmin(skill, actingUser);

        skillRepository.updateStatus(skillId, status);

        // 安全事件一律 WARN 起步(backend/CLAUDE.md 6.6):下架 / 恢复直接影响一个 skill 是否还能被公开发现,
        // 值得留一条能被巡检看到的记录,而不是淹没在 DEBUG 里。
        log.warn("skill 状态已变更, skillId={}, status={}, operator={}", skillId, status, actingUser.userId());
        auditLog.info(
                "skill 状态变更, event={}, skillId={}, status={}, operator={}",
                "skill_status_changed",
                skillId,
                status,
                actingUser.userId());
        return requireSkill(skillId);
    }

    private void requireOwnerOrAdmin(Skill skill, AuthenticatedUser actingUser) {
        boolean allowed = actingUser.isAdmin() || actingUser.userId() == skill.owner().userId();
        if (!allowed) {
            throw new AccessDeniedException("只有 owner 或 ADMIN 能操作这个 skill");
        }
    }

    private Skill requireSkill(long skillId) {
        return skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
    }

    private ResourceNotFoundException notFound(long skillId) {
        return new ResourceNotFoundException(SKILL_NOT_FOUND_CODE, "skill 不存在, id=" + skillId);
    }

    private int normalizePage(Integer pageParam) {
        if (pageParam == null || pageParam < MIN_PAGE) {
            return DEFAULT_PAGE;
        }
        return pageParam;
    }

    private int normalizeSize(Integer sizeParam) {
        if (sizeParam == null || sizeParam < MIN_SIZE) {
            return DEFAULT_SIZE;
        }
        return Math.min(sizeParam, MAX_SIZE);
    }
}
