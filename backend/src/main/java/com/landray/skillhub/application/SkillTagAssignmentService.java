package com.landray.skillhub.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.landray.skillhub.domain.exception.TagSelectionInvalidException;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagName;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.domain.repository.TagRepository;

/**
 * skill 打标签的编排逻辑(业务建模文档 5.7/5.15),被"发布新 skill"与"改 skill 元数据"两条路径共用。
 *
 * <p>不是"只有一行转发"的领域服务,而是真实的编排:数量校验 + CURATED 存在性与 kind 校验 + FREE
 * 找不到就创建 + 替换关联 + 清理孤儿 FREE 标签,跨了 {@code tag} 与 {@code skill_tag} 两张表,符合
 * backend/CLAUDE.md 第二节"简单 CRUD 允许 application 直接调仓储"的反面情形——这里不简单。
 *
 * <p>本类自身不加 {@code @Transactional}:事务边界在调用方(backend/CLAUDE.md 第五节),本类的方法总是在
 * 调用方已经开启的事务里执行,依赖 Spring 的事务传播机制(默认 REQUIRED)自然join 进同一个连接。
 */
@Service
public class SkillTagAssignmentService {

    /**
     * CURATED 分类的上限。<b>没有下限——一个都不选是合法的</b>(业务建模文档 5.7)。
     *
     * <p>上一版是必选 1~3,理由是"CURATED 是导航分类,没有分类的 skill 不出现在任何分类下"。那个后果是真的,
     * 但它是<b>可见的、可事后修复的</b> —— 作者随时能补,而且没分类的 skill 仍然出现在默认列表与搜索里
     * (标签筛选是可选条件,不筛时不影响)。反过来,必选把成本压在了最不合适的时刻:上传的人往往还没想好归到
     * 哪一类,却被拦在门外,只能随便挑一个凑数。<b>随手凑的分类比没有分类更糟</b> —— 它污染导航,而且没人会
     * 回头订正一个"看起来已经填了"的字段。
     */
    private static final int MAX_CURATED = TagKind.CURATED.maxPerSkill();
    private static final int MAX_FREE = TagKind.FREE.maxPerSkill();

    private final TagRepository tagRepository;
    private final SkillRepository skillRepository;

    public SkillTagAssignmentService(TagRepository tagRepository, SkillRepository skillRepository) {
        this.tagRepository = tagRepository;
        this.skillRepository = skillRepository;
    }

    /**
     * 校验数量约束(CURATED 至多 3、FREE 至多 10)并把 CURATED 标签 id + FREE 标签名解析成最终的 tag id 列表。
     * FREE 标签不存在则自动创建。两者都可以为空。
     */
    public List<Long> resolveTagIds(List<Long> curatedTagIds, List<String> freeTagNames, long actingUserId) {
        List<Long> normalizedCurated = curatedTagIds == null ? List.of() : List.copyOf(curatedTagIds);
        List<String> normalizedFreeNames = normalizeFreeNames(freeTagNames);

        if (normalizedCurated.size() > MAX_CURATED) {
            throw new TagSelectionInvalidException(
                    "CURATED 标签至多 " + MAX_CURATED + " 个,实际为 " + normalizedCurated.size());
        }
        if (normalizedFreeNames.size() > MAX_FREE) {
            throw new TagSelectionInvalidException(
                    "FREE 标签至多 " + MAX_FREE + " 个,实际为 " + normalizedFreeNames.size());
        }

        validateCuratedTags(normalizedCurated);

        List<Long> resolvedFreeIds = new ArrayList<>();
        for (String freeName : normalizedFreeNames) {
            Tag tag = tagRepository.findOrCreateFreeTag(freeName, actingUserId);
            resolvedFreeIds.add(tag.id());
        }

        List<Long> result = new ArrayList<>(normalizedCurated);
        result.addAll(resolvedFreeIds);
        return List.copyOf(result);
    }

    /** 整体替换 skill 的标签集合,并清理本次操作中失去全部引用的 FREE 标签(业务建模文档 5.7)。 */
    public void replaceSkillTags(long skillId, List<Long> newTagIds) {
        List<Long> oldTagIds = skillRepository.replaceTags(skillId, newTagIds);
        for (Long oldTagId : oldTagIds) {
            if (!newTagIds.contains(oldTagId)) {
                tagRepository.deleteFreeTagIfOrphaned(oldTagId);
            }
        }
    }

    private void validateCuratedTags(List<Long> curatedTagIds) {
        List<Tag> curatedTags = tagRepository.findByIds(curatedTagIds);
        if (curatedTags.size() != curatedTagIds.size()) {
            throw new TagSelectionInvalidException("存在不存在的 CURATED 标签 id");
        }
        for (Tag tag : curatedTags) {
            if (tag.kind() != TagKind.CURATED) {
                throw new TagSelectionInvalidException("标签 " + tag.name() + " 不是 CURATED 类型,不能作为分类使用");
            }
        }
    }

    /**
     * 归一化并去重。归一化规则本身在 {@link TagName} 里,这里只负责整份清单的处理:跳过 null 与纯空白项、
     * 按归一化<b>之后</b>的值去重。
     *
     * <p>空白项跳过而不报错——输入框里多敲一个逗号是手滑,拦下来只会让人反复试。超长则必须报错:那是内容
     * 本身不合法,静默截断会让作者以为存进去的是完整名字。
     */
    private List<String> normalizeFreeNames(List<String> freeTagNames) {
        if (freeTagNames == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : freeTagNames) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = new TagName(raw).value();
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }
}
