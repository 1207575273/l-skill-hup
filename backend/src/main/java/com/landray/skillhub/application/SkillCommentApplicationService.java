package com.landray.skillhub.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.exception.CommentReplyTargetInvalidException;
import com.landray.skillhub.domain.model.CommentContent;
import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.domain.repository.SkillCommentRepository;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.model.PageResult;

/**
 * 评论用例编排(业务建模文档 5.11)。事务边界只在这一层(backend/CLAUDE.md 第五节)。
 *
 * <p><b>删除权限与 skill 的写侧不是同一条规则,别照抄。</b>skill 的改元数据 / 上下架是 owner 或 ADMIN;
 * 评论是<b>评论作者本人</b>或 ADMIN(权限矩阵 5.16:"删评论 | 否 | 仅自己的 | 仅自己的 | 全部")。
 * 注意 skill 的 owner 并不能删别人在他 skill 下的评论 —— 那会让"我的地盘我说了算"变成一种删除异见的手段,
 * 而评论区的价值恰恰在于它不受被评论者控制。
 */
@Service
public class SkillCommentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillCommentApplicationService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MIN_PAGE = 1;
    private static final int MIN_SIZE = 1;

    private static final String SKILL_NOT_FOUND_CODE = "SKILL_NOT_FOUND";
    private static final String COMMENT_NOT_FOUND_CODE = "COMMENT_NOT_FOUND";

    private final SkillCommentRepository skillCommentRepository;
    private final SkillRepository skillRepository;

    public SkillCommentApplicationService(
            SkillCommentRepository skillCommentRepository, SkillRepository skillRepository) {
        this.skillCommentRepository = skillCommentRepository;
        this.skillRepository = skillRepository;
    }

    /** 评论列表,匿名可用(权限矩阵 5.16 里读侧不要求登录)。 */
    @Transactional(readOnly = true)
    public PageResult<SkillComment> listComments(long skillId, Integer pageParam, Integer sizeParam) {
        requireSkillExists(skillId);
        return skillCommentRepository.findBySkillId(skillId, normalizePage(pageParam), normalizeSize(sizeParam));
    }

    /**
     * 发表评论或回复。
     *
     * <p>{@code parentId} 非空时校验"被指向的评论是该 skill 下未删除的顶级评论"——这条一层回复的不变量
     * <b>数据库拦不住</b>(本项目没有物理外键),只能在这里守。漏了它的后果不是报错,而是长出一棵任意深度的
     * 回复树,而前端只渲染两层,多出来的层级会静默消失。
     */
    @Transactional
    public SkillComment addComment(long skillId, Long parentId, String rawContent, AuthenticatedUser actingUser) {
        requireSkillExists(skillId);
        CommentContent content = new CommentContent(rawContent);
        if (parentId != null) {
            requireReplyableTopLevel(parentId, skillId);
        }

        long commentId = skillCommentRepository.insert(skillId, actingUser.userId(), parentId, content.value());
        skillRepository.adjustCommentCount(skillId, 1);

        log.info(
                "评论已发表, skillId={}, commentId={}, parentId={}, operator={}",
                skillId,
                commentId,
                parentId,
                actingUser.userId());
        return skillCommentRepository
                .findById(commentId)
                .orElseThrow(() -> new IllegalStateException("刚插入的评论查不到, commentId=" + commentId));
    }

    /**
     * 软删除评论(作者本人 / ADMIN)。
     *
     * <p>计数只在<b>真的改了行</b>时才扣:重复删除时仓储返回 0,此时不动 {@code comment_count}。
     * 少了这个判断,连点两次删除就会把冗余计数扣成偏小甚至负数,而冗余计数错了没有任何地方会报错。
     */
    @Transactional
    public void deleteComment(long commentId, AuthenticatedUser actingUser) {
        SkillComment comment = skillCommentRepository
                .findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        COMMENT_NOT_FOUND_CODE, "评论不存在, commentId=" + commentId));
        requireAuthorOrAdmin(comment, actingUser);

        int affected = skillCommentRepository.softDelete(commentId);
        if (affected == 0) {
            // 已经是 DELETED。不报错:调用方要的结果(这条评论不再显示)已经达成,
            // 报错只会让重复点击变成一次看起来像故障的失败。
            return;
        }
        skillRepository.adjustCommentCount(comment.skillId(), -1);
        log.info(
                "评论已删除, commentId={}, skillId={}, operator={}",
                commentId,
                comment.skillId(),
                actingUser.userId());
    }

    private void requireSkillExists(long skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException(SKILL_NOT_FOUND_CODE, "skill 不存在, skillId=" + skillId);
        }
    }

    private void requireReplyableTopLevel(long parentId, long skillId) {
        if (!skillCommentRepository.isReplyableTopLevel(parentId, skillId)) {
            throw new CommentReplyTargetInvalidException();
        }
    }

    private void requireAuthorOrAdmin(SkillComment comment, AuthenticatedUser actingUser) {
        boolean isAuthor = comment.author() != null && comment.author().userId() == actingUser.userId();
        if (!actingUser.isAdmin() && !isAuthor) {
            throw new AccessDeniedException("只有评论作者本人或 ADMIN 能删除评论");
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < MIN_PAGE ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < MIN_SIZE) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
