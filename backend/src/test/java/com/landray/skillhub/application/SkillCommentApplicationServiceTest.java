package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.exception.CommentContentInvalidException;
import com.landray.skillhub.domain.exception.CommentReplyTargetInvalidException;
import com.landray.skillhub.domain.model.CommentStatus;
import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.SkillCommentRepository;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;

/** Mockito mock 掉仓储,只验编排、权限与计数维护(backend/CLAUDE.md 8.1)。 */
@ExtendWith(MockitoExtension.class)
class SkillCommentApplicationServiceTest {

    private static final long SKILL_ID = 1L;
    private static final long AUTHOR_ID = 2L;

    @Mock
    private SkillCommentRepository skillCommentRepository;

    @Mock
    private SkillRepository skillRepository;

    private SkillCommentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SkillCommentApplicationService(skillCommentRepository, skillRepository);
    }

    private AuthenticatedUser user(long userId, UserRole role) {
        return new AuthenticatedUser(userId, role, 10L, 100L);
    }

    private SkillComment comment(long id, Long parentId, long authorId, CommentStatus status) {
        return new SkillComment(
                id,
                SKILL_ID,
                parentId,
                new SkillOwner(authorId, "张三", "80380486"),
                status == CommentStatus.DELETED ? null : "正文",
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                List.of());
    }

    // ------------------------------------------------------------------
    // 发表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("发表评论时 comment_count 加一,同一事务内")
    void should_incrementCommentCount_when_commentAdded() {
        when(skillRepository.existsById(SKILL_ID)).thenReturn(true);
        when(skillCommentRepository.insert(SKILL_ID, AUTHOR_ID, null, "说点什么")).thenReturn(7L);
        when(skillCommentRepository.findById(7L))
                .thenReturn(Optional.of(comment(7L, null, AUTHOR_ID, CommentStatus.NORMAL)));

        service.addComment(SKILL_ID, null, "说点什么", user(AUTHOR_ID, UserRole.USER));

        verify(skillRepository).adjustCommentCount(SKILL_ID, 1);
    }

    @Test
    @DisplayName("skill 不存在时 404,不插任何行")
    void should_reject_when_skillNotFound() {
        when(skillRepository.existsById(SKILL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.addComment(SKILL_ID, null, "内容", user(AUTHOR_ID, UserRole.USER)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(skillCommentRepository, never()).insert(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("正文非法时在碰仓储之前就拒绝——校验在领域层,不是等数据库报错")
    void should_rejectBeforeTouchingRepository_when_contentBlank() {
        when(skillRepository.existsById(SKILL_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.addComment(SKILL_ID, null, "   ", user(AUTHOR_ID, UserRole.USER)))
                .isInstanceOf(CommentContentInvalidException.class);
        verify(skillCommentRepository, never()).insert(anyLong(), anyLong(), any(), any());
        verify(skillRepository, never()).adjustCommentCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("回复合法的顶级评论时正常插入")
    void should_insertReply_when_parentIsReplyableTopLevel() {
        when(skillRepository.existsById(SKILL_ID)).thenReturn(true);
        when(skillCommentRepository.isReplyableTopLevel(5L, SKILL_ID)).thenReturn(true);
        when(skillCommentRepository.insert(SKILL_ID, AUTHOR_ID, 5L, "回复")).thenReturn(8L);
        when(skillCommentRepository.findById(8L))
                .thenReturn(Optional.of(comment(8L, 5L, AUTHOR_ID, CommentStatus.NORMAL)));

        SkillComment reply = service.addComment(SKILL_ID, 5L, "回复", user(AUTHOR_ID, UserRole.USER));

        assertThat(reply.isTopLevel()).isFalse();
    }

    @Test
    @DisplayName("回复目标不是可回复的顶级评论时拒绝——一层回复的不变量数据库拦不住,只能在这里守")
    void should_reject_when_parentIsNotReplyableTopLevel() {
        when(skillRepository.existsById(SKILL_ID)).thenReturn(true);
        when(skillCommentRepository.isReplyableTopLevel(5L, SKILL_ID)).thenReturn(false);

        // 漏了这条校验的后果不是报错,而是长出一棵任意深度的回复树,
        // 而前端只渲染两层,多出来的层级会静默消失。
        assertThatThrownBy(() -> service.addComment(SKILL_ID, 5L, "回复", user(AUTHOR_ID, UserRole.USER)))
                .isInstanceOf(CommentReplyTargetInvalidException.class);
        verify(skillCommentRepository, never()).insert(anyLong(), anyLong(), any(), any());
    }

    // ------------------------------------------------------------------
    // 删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("作者本人可以删自己的评论,计数减一")
    void should_deleteAndDecrementCount_when_actingUserIsAuthor() {
        when(skillCommentRepository.findById(7L))
                .thenReturn(Optional.of(comment(7L, null, AUTHOR_ID, CommentStatus.NORMAL)));
        when(skillCommentRepository.softDelete(7L)).thenReturn(1);

        service.deleteComment(7L, user(AUTHOR_ID, UserRole.USER));

        verify(skillRepository).adjustCommentCount(SKILL_ID, -1);
    }

    @Test
    @DisplayName("ADMIN 可以删任何人的评论")
    void should_allowDelete_when_actingUserIsAdmin() {
        when(skillCommentRepository.findById(7L))
                .thenReturn(Optional.of(comment(7L, null, AUTHOR_ID, CommentStatus.NORMAL)));
        when(skillCommentRepository.softDelete(7L)).thenReturn(1);

        service.deleteComment(7L, user(999L, UserRole.ADMIN));

        verify(skillCommentRepository).softDelete(7L);
    }

    @Test
    @DisplayName("既非作者也非 ADMIN 时拒绝——注意 skill 的 owner 也不行")
    void should_rejectDelete_when_actingUserIsNeitherAuthorNorAdmin() {
        when(skillCommentRepository.findById(7L))
                .thenReturn(Optional.of(comment(7L, null, AUTHOR_ID, CommentStatus.NORMAL)));

        // skill 的 owner 能删别人在他 skill 下的评论,会让"我的地盘我说了算"变成删除异见的手段,
        // 而评论区的价值恰恰在于它不受被评论者控制。所以这里只认作者本人与 ADMIN。
        assertThatThrownBy(() -> service.deleteComment(7L, user(999L, UserRole.USER)))
                .isInstanceOf(AccessDeniedException.class);
        verify(skillCommentRepository, never()).softDelete(anyLong());
    }

    @Test
    @DisplayName("评论不存在时 404")
    void should_rejectDelete_when_commentNotFound() {
        when(skillCommentRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(7L, user(AUTHOR_ID, UserRole.USER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("重复删除不再扣计数,也不报错")
    void should_notDecrementCountAgain_when_alreadyDeleted() {
        when(skillCommentRepository.findById(7L))
                .thenReturn(Optional.of(comment(7L, null, AUTHOR_ID, CommentStatus.DELETED)));
        when(skillCommentRepository.softDelete(7L)).thenReturn(0);

        // 不报错:调用方要的结果(这条评论不再显示)已经达成,报错只会让重复点击变成一次看起来像故障的失败。
        // 不扣计数:连点两次会把冗余计数扣成偏小,而冗余计数错了没有任何地方会报错。
        service.deleteComment(7L, user(AUTHOR_ID, UserRole.USER));

        verify(skillRepository, never()).adjustCommentCount(anyLong(), anyInt());
    }

    // ------------------------------------------------------------------
    // 列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("size 超上限时夹到 100——分页参数是外部输入,解释它是这一层的职责")
    void should_clampSize_when_exceedingMax() {
        when(skillRepository.existsById(SKILL_ID)).thenReturn(true);

        service.listComments(SKILL_ID, null, 500);

        verify(skillCommentRepository).findBySkillId(SKILL_ID, 1, 100);
    }

    @Test
    @DisplayName("page/size 缺省时用默认值")
    void should_useDefaults_when_pagingParamsMissing() {
        when(skillRepository.existsById(SKILL_ID)).thenReturn(true);

        service.listComments(SKILL_ID, null, null);

        verify(skillCommentRepository).findBySkillId(SKILL_ID, 1, 20);
    }
}
