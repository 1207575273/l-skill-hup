package com.landray.skillhub.infrastructure.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.repository.SkillCommentRepository;
import com.landray.skillhub.infrastructure.persistence.mapper.SkillCommentMapper;
import com.landray.skillhub.infrastructure.persistence.po.SkillCommentRow;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@link SkillCommentRepository} 的 MyBatis 实现。
 *
 * <p>装配路径与 {@code SkillRepositoryImpl} 的标签一致:先取一页顶级评论,再按 parentId 批量取回复,
 * 在内存里分组挂回去。理由见 {@code SkillCommentMapper.xml} 顶部注释 —— 直接 JOIN 会让一条有 3 个回复的
 * 顶级评论展开成 3 行,分页语义直接错掉。
 */
@Repository
public class SkillCommentRepositoryImpl implements SkillCommentRepository {

    private final SkillCommentMapper skillCommentMapper;

    public SkillCommentRepositoryImpl(SkillCommentMapper skillCommentMapper) {
        this.skillCommentMapper = skillCommentMapper;
    }

    @Override
    public PageResult<SkillComment> findBySkillId(long skillId, int page, int size) {
        int offset = (page - 1) * size;
        List<SkillCommentRow> topLevelRows = skillCommentMapper.selectTopLevelPage(skillId, size, offset);
        long total = skillCommentMapper.countTopLevel(skillId);

        Map<Long, List<SkillComment>> repliesByParentId = fetchReplies(topLevelRows);
        List<SkillComment> items = topLevelRows.stream()
                .map(row -> toDomain(row, repliesByParentId.getOrDefault(row.id(), List.of())))
                .toList();
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public Optional<SkillComment> findById(long commentId) {
        SkillCommentRow row = skillCommentMapper.selectById(commentId);
        // 单条查询不带回复:唯一的使用者是删除前的存在性与权限判断,那里不关心它下面挂了什么。
        return Optional.ofNullable(row).map(found -> toDomain(found, List.of()));
    }

    @Override
    public long insert(long skillId, long userId, Long parentId, String content) {
        Map<String, Object> params = new HashMap<>();
        params.put("skillId", skillId);
        params.put("userId", userId);
        params.put("parentId", parentId);
        params.put("content", content);
        skillCommentMapper.insertComment(params);
        return (Long) params.get("id");
    }

    @Override
    public int softDelete(long commentId) {
        return skillCommentMapper.softDelete(commentId);
    }

    @Override
    public boolean isReplyableTopLevel(long commentId, long skillId) {
        return skillCommentMapper.countTopLevelByIdAndSkillId(commentId, skillId) > 0;
    }

    private Map<Long, List<SkillComment>> fetchReplies(List<SkillCommentRow> topLevelRows) {
        if (topLevelRows.isEmpty()) {
            // 空集合短路:MyBatis 的 foreach 展开成 `IN ()`,那是语法错误而不是"匹配不到"。
            return Map.of();
        }
        List<Long> parentIds = topLevelRows.stream().map(SkillCommentRow::id).toList();
        Map<Long, List<SkillComment>> grouped = new HashMap<>();
        for (SkillCommentRow row : skillCommentMapper.selectRepliesByParentIds(parentIds)) {
            grouped.computeIfAbsent(row.parentId(), key -> new ArrayList<>()).add(toDomain(row, List.of()));
        }
        return grouped;
    }

    private SkillComment toDomain(SkillCommentRow row, List<SkillComment> replies) {
        // author 用 LEFT JOIN 取,作者行缺失时整个 author 为 null 而不是一个字段全空的壳 ——
        // 后者会在界面上显示成一个没有名字的用户,比明确的"作者信息缺失"更难排查。
        SkillOwner author = row.authorUserId() == null
                ? null
                : new SkillOwner(row.authorUserId(), row.authorDisplayName(), row.authorEmployeeNo());
        return new SkillComment(
                row.id(),
                row.skillId(),
                row.parentId(),
                author,
                row.content(),
                row.status(),
                row.createdAt(),
                replies);
    }
}
