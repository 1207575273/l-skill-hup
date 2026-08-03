package com.landray.skillhub.interfaces.rest.comment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.SkillCommentApplicationService;
import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.CommentStatus;
import com.landray.skillhub.domain.model.SkillComment;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.interfaces.rest.auth.AuthenticationInterceptor;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.WebMvcAuthConfig;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@code @WebMvcTest} + MockMvc:只验参数绑定、状态码、鉴权分支,mock 掉 application 层
 * (backend/CLAUDE.md 8.1)。
 */
@WebMvcTest(controllers = SkillCommentController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
@Import({AuthenticationInterceptor.class, WebMvcAuthConfig.class, CurrentUserAccessor.class})
class SkillCommentControllerTest {

    private static final String VALID_TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillCommentApplicationService skillCommentApplicationService;

    /** 见 {@code SkillControllerTest} 同名字段注释:拦截器是 web 层类型,切片一定会加载,依赖不给上下文起不来。 */
    @MockitoBean
    private AuthApplicationService authApplicationService;

    private void stubValidSession(long userId, UserRole role) {
        when(authApplicationService.resolveSession(VALID_TOKEN))
                .thenReturn(Optional.of(new AuthenticatedUser(userId, role, 10L, 99L)));
    }

    private SkillComment comment(long id, Long parentId, CommentStatus status, List<SkillComment> replies) {
        return new SkillComment(
                id,
                1L,
                parentId,
                new SkillOwner(2L, "张三", "80380486"),
                status == CommentStatus.DELETED ? null : "正文内容",
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                replies);
    }

    @Test
    @DisplayName("评论列表匿名可用,回复嵌在顶级评论下")
    void should_listCommentsAnonymously_when_noToken() throws Exception {
        SkillComment top = comment(
                7L, null, CommentStatus.NORMAL, List.of(comment(8L, 7L, CommentStatus.NORMAL, List.of())));
        when(skillCommentApplicationService.listComments(eq(1L), isNull(), isNull()))
                .thenReturn(new PageResult<>(List.of(top), 1L, 1, 20));

        mockMvc.perform(get("/api/v1/skills/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].author.displayName").value("张三"))
                .andExpect(jsonPath("$.items[0].replies[0].id").value(8));
    }

    @Test
    @DisplayName("已删除的评论正文为 null 且带 deleted 标记——占位文案归前端,服务端不写死那句话")
    void should_returnNullContentWithDeletedFlag_when_commentDeleted() throws Exception {
        SkillComment deleted = comment(
                7L, null, CommentStatus.DELETED, List.of(comment(8L, 7L, CommentStatus.NORMAL, List.of())));
        when(skillCommentApplicationService.listComments(eq(1L), isNull(), isNull()))
                .thenReturn(new PageResult<>(List.of(deleted), 1L, 1, 20));

        mockMvc.perform(get("/api/v1/skills/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.items[0].replies[0].content").value("正文内容"));
    }

    @Test
    @DisplayName("回复的 replies 是空数组而不是 null——前端不用为两种『没有回复』写两套判断")
    void should_returnEmptyArrayForReplies_when_commentIsItselfAReply() throws Exception {
        when(skillCommentApplicationService.listComments(eq(1L), isNull(), isNull()))
                .thenReturn(new PageResult<>(
                        List.of(comment(7L, null, CommentStatus.NORMAL, List.of())), 1L, 1, 20));

        mockMvc.perform(get("/api/v1/skills/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].replies").isArray())
                .andExpect(jsonPath("$.items[0].replies").isEmpty());
    }

    @Test
    @DisplayName("发表评论需登录,parentId 与正文原样透传")
    void should_addComment_when_tokenValid() throws Exception {
        stubValidSession(2L, UserRole.USER);
        when(skillCommentApplicationService.addComment(eq(1L), eq(5L), eq("说点什么"), any()))
                .thenReturn(comment(9L, 5L, CommentStatus.NORMAL, List.of()));

        mockMvc.perform(post("/api/v1/skills/1/comments")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType("application/json")
                        .content("{\"parentId\":5,\"content\":\"说点什么\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    @DisplayName("未登录发评论直接 401,不进 application 层")
    void should_rejectAddComment_when_tokenMissing() throws Exception {
        mockMvc.perform(post("/api/v1/skills/1/comments")
                        .contentType("application/json")
                        .content("{\"content\":\"说点什么\"}"))
                .andExpect(status().isUnauthorized());

        verify(skillCommentApplicationService, never()).addComment(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("删除评论返回 204,而不是一份已删除评论的空壳")
    void should_deleteComment_when_tokenValid() throws Exception {
        stubValidSession(2L, UserRole.USER);

        mockMvc.perform(post("/api/v1/comments/7/delete").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(skillCommentApplicationService).deleteComment(eq(7L), any());
    }

    @Test
    @DisplayName("未登录删评论直接 401")
    void should_rejectDeleteComment_when_tokenMissing() throws Exception {
        mockMvc.perform(post("/api/v1/comments/7/delete")).andExpect(status().isUnauthorized());

        verify(skillCommentApplicationService, never()).deleteComment(anyLong(), any());
    }
}
