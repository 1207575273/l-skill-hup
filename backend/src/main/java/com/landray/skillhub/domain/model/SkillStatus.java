package com.landray.skillhub.domain.model;

import java.util.Locale;

import com.landray.skillhub.domain.exception.InvalidSkillStatusException;

/**
 * skill 的上下架状态。
 *
 * <p>业务建模文档 4.8:下架后不出现在列表,已生成的安装单仍可用但脚本会提示。下架<b>可逆</b>。
 *
 * <p><b>这个枚举里没有 DELETED,因为删除是物理删除,不是一个状态。</b>类注释上一版写着"不提供物理删除",
 * 那条已按需求推翻:见 {@code SkillApplicationService#delete}。删除走的是真正删行 + 删制品,所以它不会在这里
 * 留下任何痕迹 —— 想找一个 skill 被删掉的记录,去 {@code skillhub.audit} 的 {@code skill_deleted} 事件里找,
 * 那是唯一的落点。
 */
public enum SkillStatus {
    /** 正常展示,出现在列表与搜索中。 */
    PUBLISHED,
    /** 已下架:不出现在列表 / 搜索,但仍可通过已知的直达链接(详情页、安装单)访问。 */
    UNLISTED;

    /** 管理视图的 {@code status} 筛选参数。与 {@code SkillSource#fromParam} 同一形态:非法值给带上下文的业务异常。 */
    public static SkillStatus fromParam(String raw) {
        try {
            return SkillStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidSkillStatusException("不支持的状态筛选, status=" + raw);
        }
    }
}
