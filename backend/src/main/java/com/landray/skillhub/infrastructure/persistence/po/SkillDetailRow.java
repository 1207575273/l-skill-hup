package com.landray.skillhub.infrastructure.persistence.po;

import java.time.Instant;
import java.util.List;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;

/**
 * {@code SkillMapper.selectDetailById} 的扁平查询结果行,含最新版本全部字段(正文 + 认证信息)。
 *
 * <p>与 {@link SkillListRow} 是两条不同的查询,不是同一行按需裁剪——详情页需要 {@code skill_md_content} /
 * {@code readme_content} 这类重字段,列表页刻意不选它们(业务建模文档 5.6 附近反复强调的"不能让重内容进列表页
 * 响应")。两个 PO 分开定义,SQL 层面也就自然分成两条 SELECT,不存在"一份大查询里挑字段用"的隐患。
 */
public record SkillDetailRow(
        Long id,
        String name,
        String displayName,
        String description,
        SkillSource source,
        Long ownerUserId,
        String ownerDisplayName,
        String ownerEmployeeNo,
        String cardExcerpt,
        SkillStatus status,
        long downloadCount,
        int likeCount,
        int dislikeCount,
        int favoriteCount,
        int commentCount,
        Instant createdAt,
        Instant updatedAt,
        Long latestVersionId,
        String latestVersionVersion,
        Long latestVersionSizeBytes,
        Integer latestVersionFileCount,
        List<String> latestVersionAllowedTools,
        String latestVersionSkillMdContent,
        String latestVersionReadmeContent,
        String latestVersionChangelog,
        Certification latestVersionCertification,
        Long latestVersionCertifiedByUserId,
        Instant latestVersionCertifiedAt,
        String latestVersionCertifyNote,
        Long latestVersionCreatedByUserId,
        Instant latestVersionCreatedAt) {}
