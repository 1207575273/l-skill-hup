package com.landray.skillhub.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 读侧用例编排:列表、详情、版本列表。事务边界只在这一层(backend/CLAUDE.md 第五节)。
 *
 * <p>page / size 的默认值与夹紧(size 上限 100)在这里做,不在 {@link SkillSearchCriteria} 的紧凑构造器里——
 * "把不合法的外部输入变成合法值"是解释请求的职责,{@code SkillSearchCriteria} 只负责"值一旦构造出来就合法"。
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

    public SkillApplicationService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
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
