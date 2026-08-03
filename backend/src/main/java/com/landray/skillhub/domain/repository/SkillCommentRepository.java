package com.landray.skillhub.domain.repository;

import java.util.Optional;

import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.shared.model.PageResult;

/**
 * 评论仓储(业务建模文档 5.11)。
 *
 * <p><b>分页的口径是"顶级评论",不是"评论行"。</b>回复随它的顶级评论一起返回,不单独分页 —— 一层回复本来就
 * 不会多到需要分页,而按行分页会把一段对话切到两页上,那是比"某页多几行"糟得多的体验。所以
 * {@link PageResult#total()} 是顶级评论数。
 */
public interface SkillCommentRepository {

    /**
     * 某个 skill 的评论,顶级按时间倒序分页,每条顶级评论带上它的回复(回复按时间正序 —— 对话要顺着读)。
     *
     * <p><b>已删除的行按两条规则过滤,不是无脑全返回:</b>
     *
     * <ul>
     *   <li>已删除的<b>回复</b>直接不返回。它下面不可能再挂东西(只允许一层),留着占位没有任何人受益。
     *   <li>已删除的<b>顶级评论</b>只在"还有未删除的回复"时保留,正文置空。软删除存在的唯一理由就是不让回复悬空;
     *       没有回复可悬空时,一条"该评论已删除"的占位就是纯噪音,而这类噪音会随时间单调累积。
     * </ul>
     */
    PageResult<SkillComment> findBySkillId(long skillId, int page, int size);

    /** 按 id 查,用于删除前的存在性与权限判断。已删除的也会返回 —— 调用方要能区分"不存在"与"已经删过了"。 */
    Optional<SkillComment> findById(long commentId);

    /**
     * 插入并返回新 id。
     *
     * @param parentId 顶级评论传 {@code null};一层回复的不变量由调用方在此之前校验
     */
    long insert(long skillId, long userId, Long parentId, String content);

    /** 软删除:只改 {@code status},保留行。返回受影响行数,已经是 DELETED 时为 0(幂等,不重复扣计数)。 */
    int softDelete(long commentId);

    /**
     * 该 id 是否是这个 skill 下<b>未删除的顶级评论</b> —— 一层回复不变量的判据(业务建模文档 5.11)。
     *
     * <p>四个条件揉成一个布尔值而不是分别暴露:调用方拿到 false 之后的动作只有一个(拒绝),
     * 拆开只会让调用方有机会漏判其中一条,而漏判的后果是长出一棵任意深度的回复树。
     */
    boolean isReplyableTopLevel(long commentId, long skillId);
}
