package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;

import com.landray.skillhub.domain.model.Certification;

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
 * <p>字段刻意只覆盖业务建模文档 7.1 卡片所需的最小集合(不含 description / dislikeCount / commentCount /
 * status),避免把详情页才需要的重字段(尤其是 skill_md_content / readme_content)带进分页列表查询。
 */
public record SkillListRow(
        Long id,
        String name,
        String displayName,
        String cardExcerpt,
        long downloadCount,
        int likeCount,
        int favoriteCount,
        Instant updatedAt,
        Long ownerUserId,
        String ownerDisplayName,
        String ownerEmployeeNo,
        Long latestVersionId,
        String latestVersionVersion,
        Certification latestVersionCertification) {}
