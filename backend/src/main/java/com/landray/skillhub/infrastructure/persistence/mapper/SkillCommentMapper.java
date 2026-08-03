package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.infrastructure.persistence.po.SkillCommentRow;

/**
 * 评论读写。SQL 全在 {@code resources/mapper/SkillCommentMapper.xml} —— 每一条都带条件或 join,
 * 按 backend/CLAUDE.md 第三节不允许用注解 SQL。
 */
@Mapper
public interface SkillCommentMapper {

    /** 顶级评论一页(倒序)。分页口径是顶级评论,不是评论行,理由见 {@code SkillCommentRepository} 类注释。 */
    List<SkillCommentRow> selectTopLevelPage(
            @Param("skillId") long skillId, @Param("limit") int limit, @Param("offset") int offset);

    /** 这一页顶级评论各自的回复(正序)。空集合由调用方短路,不进 SQL。 */
    List<SkillCommentRow> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    /** 顶级评论总数,口径与 {@link #selectTopLevelPage} 完全一致,否则分页会出现"有页码点进去是空的"。 */
    long countTopLevel(@Param("skillId") long skillId);

    SkillCommentRow selectById(@Param("commentId") long commentId);

    /** 见 {@code AppUserMapper} 类注释:{@code useGeneratedKeys} 要把生成的 id 写回参数 Map,record 做不到。 */
    int insertComment(Map<String, Object> params);

    /** 软删除。带 {@code status = 'NORMAL'} 条件,重复删除返回 0,让调用方能据此不重复扣 comment_count。 */
    int softDelete(@Param("commentId") long commentId);

    /** 是否是顶级评论且属于该 skill —— 一层回复不变量的判据(业务建模文档 5.11)。 */
    int countTopLevelByIdAndSkillId(@Param("commentId") long commentId, @Param("skillId") long skillId);
}
