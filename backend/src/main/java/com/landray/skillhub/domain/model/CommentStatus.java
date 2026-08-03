package com.landray.skillhub.domain.model;

/**
 * 评论状态。业务建模文档 5.11:删除是<b>软删除</b>。
 *
 * <p>软删除保留行而不是删行的唯一理由:<b>被删的顶级评论如果有回复,物理删会让回复悬空</b>。回复的
 * {@code parent_id} 指向一个已经不存在的 id,展示时要么崩要么静默丢掉一串对话。
 *
 * <p>这条理由决定了它的适用边界 —— 没有回复可悬空时,保留一个"该评论已删除"的占位是纯噪音,
 * 所以查询侧会把这类行过滤掉,见 {@code SkillCommentMapper.xml}。
 */
public enum CommentStatus {
    /** 正常显示。 */
    NORMAL,
    /** 已删除:正文不再返回给前端,但行保留,以免它的回复悬空。 */
    DELETED
}
