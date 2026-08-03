package com.landray.skillhub.domain.repository;

import java.util.List;
import java.util.Optional;

import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 的仓储接口。只有接口,纯 Java,实现在 {@code infrastructure.persistence}。
 *
 * <p>本轮只落地读侧四个方法,写侧(新增、发版、下架等)由后续任务补齐——按需扩展接口,不预先占位。
 */
public interface SkillRepository {

    /** 列表查询,分页 + 筛选 + 排序,返回值不含正文与标签外的重内容(卡片轻量视图)。 */
    PageResult<Skill> search(SkillSearchCriteria criteria);

    /** 详情查询,含最新版本全部字段(正文、认证信息)与全部标签。{@code UNLISTED} 的 skill 同样可查——只是不进列表。 */
    Optional<Skill> findDetailById(long skillId);

    /** skill 是否存在,用于版本列表接口在返回空数组前先判断"skill 压根不存在"应返回 404。 */
    boolean existsById(long skillId);

    /** 某个 skill 的全部版本,按发布时间倒序,含各版本认证状态。不含正文(见 {@link SkillVersion} 类注释)。 */
    List<SkillVersion> findVersionsBySkillId(long skillId);
}
