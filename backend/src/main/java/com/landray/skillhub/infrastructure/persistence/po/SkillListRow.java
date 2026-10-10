package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.SkillStatus;

/**
 * {@code SkillMapper.selectListPage} 的扁平查询结果行。
 *
 * <p>PO 而非直接映射进 {@link com.landray.skillhub.domain.model.Skill}:列表查询是 {@code skill} join
 * {@code app_user}(取归属人)join {@code skill_version}(取最新版本认证状态)的扁平结果,而
 * {@code Skill} 领域模型是嵌套结构({@code owner} / {@code latestVersion} / {@code tags} 三个子对象)。
 * 表结构(扁平)与领域模型(嵌套)对不上,这正是 backend/CLAUDE.md 第二节允许在 infrastructure/persistence
 * 加 PO 的场景。{@code tags} 不在这里——由 {@code SkillRepositoryImpl} 另发一次批量查询后再拼装,理由见
 * 该类的类注释。
 *
 * <p>字段刻意只覆盖卡片与管理表格所需的最小集合(不含 description / dislikeCount / commentCount),避免把
 * 详情页才需要的重字段(尤其是 skill_md_content / readme_content)带进分页列表查询。
 *
 * <p><b>{@code status} 曾经不在这里,那是个 bug。</b>当时列表查询的过滤条件写死 {@code status='PUBLISHED'},
 * 查出来的行必然都已发布,所以 {@code SkillRepositoryImpl} 直接填了常量 {@code PUBLISHED} —— 在那个前提下
 * 两者等价。管理视图放开状态筛选后前提消失,常量就成了谎话:库里 {@code UNLISTED} 的行读回来是
 * {@code PUBLISHED},界面上表现为"下架了但状态没变、按钮也不变"。
 *
 * <p>教训:<b>把"当前查询恒为某值"固化成常量,等于给这段代码绑了一个没写下来的前提。</b>前提被放宽时不会有
 * 任何编译错误或测试失败来提醒你 —— 除非测试断言的正是那个维度。
 */
public record SkillListRow(
        Long id,
        String name,
        String displayName,
        String cardExcerpt,
        SkillStatus status,
        long downloadCount,
        int likeCount,
        int favoriteCount,
        Instant updatedAt,
        Long ownerUserId,
        String ownerDisplayName,
        String ownerEmployeeNo,
        String declaredOwnerName,
        String declaredOwnerEmployeeNo,
        Long latestVersionId,
        String latestVersionVersion,
        Certification latestVersionCertification) {}
