package com.landray.skillhub.interfaces.rest.tag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.TagApplicationService;
import com.landray.skillhub.application.TagGovernanceApplicationService;
import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.interfaces.rest.auth.AuthenticationInterceptor;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.WebMvcAuthConfig;

/**
 * {@code @WebMvcTest} + MockMvc,mock 掉 application 层。能不 mock 整个 MyBatis 层就跑起来的理由见
 * {@code SkillControllerTest} 类注释——两者是同一个问题(Mapper 扫描配置的位置)。
 *
 * <p>连 {@code AuthenticationInterceptor} 与 {@code WebMvcAuthConfig} 一起 {@code @Import}:这个 Controller
 * 里读侧匿名、治理侧 ADMIN,<b>两种鉴权形态共存于同一个类</b>,不把拦截器装进切片就等于把治理接口的权限
 * 测试全部跳过——而那是这批接口最不能出错的地方。
 */
@WebMvcTest(controllers = TagController.class)
@Import({AuthenticationInterceptor.class, WebMvcAuthConfig.class, CurrentUserAccessor.class})
@TestPropertySource(properties = "server.servlet.context-path=")
class TagControllerTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_TOKEN = "user-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagApplicationService tagApplicationService;

    @MockitoBean
    private TagGovernanceApplicationService tagGovernanceApplicationService;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    private void givenAdminSession() {
        when(authApplicationService.resolveSession(ADMIN_TOKEN))
                .thenReturn(Optional.of(new AuthenticatedUser(9L, UserRole.ADMIN, 10L, 99L)));
    }

    private void givenNormalUserSession() {
        when(authApplicationService.resolveSession(USER_TOKEN))
                .thenReturn(Optional.of(new AuthenticatedUser(2L, UserRole.USER, 10L, 99L)));
    }

    // ---------- 读侧:匿名可用 ----------

    @Test
    @DisplayName("不带 kind 参数时返回全部词条,附带 skillCount")
    void should_returnAllTags_whenKindParamAbsent() throws Exception {
        Tag tag = new Tag(1L, "代码审查", TagKind.CURATED, "评审清单", 20, 1L, null, null);
        when(tagApplicationService.listTags(null)).thenReturn(List.of(new TagWithSkillCount(tag, 5L)));

        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("代码审查"))
                .andExpect(jsonPath("$[0].kind").value("CURATED"))
                .andExpect(jsonPath("$[0].skillCount").value(5));
    }

    @Test
    @DisplayName("kind 参数原样透传给 application 层")
    void should_passKindParamThrough_whenListingTags() throws Exception {
        when(tagApplicationService.listTags("free")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tags").param("kind", "free")).andExpect(status().isOk());

        verify(tagApplicationService).listTags("free");
    }

    /** 上传页在用户逐字输入 FREE 标签时调它,那条路径上不该有登录门槛。 */
    @Test
    @DisplayName("联想接口匿名可访问,三个参数原样透传")
    void should_allowAnonymousSuggest_andPassParamsThrough() throws Exception {
        when(tagGovernanceApplicationService.suggest(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tags/suggest")
                        .param("keyword", "code")
                        .param("kind", "FREE")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(tagGovernanceApplicationService).suggest("code", "FREE", 5);
    }

    // ---------- 治理侧:ADMIN 专属 ----------

    @Test
    @DisplayName("未登录建词条返回 401,不落到 application 层")
    void should_reject_whenCreatingTagAnonymously() throws Exception {
        mockMvc.perform(post("/api/v1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新分类\"}"))
                .andExpect(status().isUnauthorized());

        verify(tagGovernanceApplicationService, never()).createCuratedTag(any(), any(), any(), any());
    }

    @Test
    @DisplayName("普通用户建词条返回 403")
    void should_reject_whenCreatingTagAsNormalUser() throws Exception {
        givenNormalUserSession();

        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + USER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新分类\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(tagGovernanceApplicationService, never()).createCuratedTag(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ADMIN 建词条时请求体字段绑定正确")
    void should_bindRequestBody_whenAdminCreatesTag() throws Exception {
        givenAdminSession();
        when(tagGovernanceApplicationService.createCuratedTag(any(), any(), any(), any()))
                .thenReturn(new Tag(11L, "性能优化", TagKind.CURATED, "压测与调优", 95, 9L, null, null));

        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"性能优化\",\"description\":\"压测与调优\",\"sortOrder\":95}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.kind").value("CURATED"))
                .andExpect(jsonPath("$.skillCount").value(0));

        verify(tagGovernanceApplicationService).createCuratedTag(eq("性能优化"), eq("压测与调优"), eq(95), any());
    }

    @Test
    @DisplayName("建词条时 name 为空返回 400")
    void should_reject_whenCreatingTagWithBlankName() throws Exception {
        givenAdminSession();

        mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    /** {@code confirmed} 缺省要落成 {@code false},漏了这条会把"知情后确认"变成"默认已确认"。 */
    @Test
    @DisplayName("更新时 confirmed 缺省为 false")
    void should_defaultConfirmedToFalse_whenAbsent() throws Exception {
        givenAdminSession();
        when(tagGovernanceApplicationService.updateTag(
                        anyLong(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new Tag(1L, "测试", TagKind.CURATED, null, 0, 9L, null, null));

        mockMvc.perform(post("/api/v1/tags/1/update")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试\"}"))
                .andExpect(status().isOk());

        verify(tagGovernanceApplicationService)
                .updateTag(eq(1L), eq("测试"), isNull(), isNull(), isNull(), eq(false), any());
    }

    @Test
    @DisplayName("更新时 confirmed=true 原样传到 application 层")
    void should_passConfirmedThrough_whenAdminConfirms() throws Exception {
        givenAdminSession();
        when(tagGovernanceApplicationService.updateTag(
                        anyLong(), any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new Tag(1L, "测试", TagKind.FREE, null, 0, 9L, null, null));

        mockMvc.perform(post("/api/v1/tags/1/update")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试\",\"kind\":\"FREE\",\"confirmed\":true}"))
                .andExpect(status().isOk());

        verify(tagGovernanceApplicationService)
                .updateTag(eq(1L), eq("测试"), eq("FREE"), isNull(), isNull(), eq(true), any());
    }

    @Test
    @DisplayName("更新时 kind 值非法返回 400")
    void should_reject_whenUpdatingWithUnknownKind() throws Exception {
        givenAdminSession();

        mockMvc.perform(post("/api/v1/tags/1/update")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试\",\"kind\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("删除返回 204,migrateToTagId 原样透传")
    void should_returnNoContent_whenAdminDeletesTag() throws Exception {
        givenAdminSession();

        mockMvc.perform(post("/api/v1/tags/1/delete")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"migrateToTagId\":2}"))
                .andExpect(status().isNoContent());

        verify(tagGovernanceApplicationService).deleteTag(eq(1L), eq(2L), any());
    }

    /** 删一个零引用的词条不需要迁移目标,前端会连请求体都不发。 */
    @Test
    @DisplayName("删除请求体整体缺省时 migrateToTagId 为 null")
    void should_acceptMissingBody_whenDeletingTag() throws Exception {
        givenAdminSession();

        mockMvc.perform(post("/api/v1/tags/1/delete").header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isNoContent());

        verify(tagGovernanceApplicationService).deleteTag(eq(1L), isNull(), any());
    }

    @Test
    @DisplayName("普通用户删词条返回 403")
    void should_reject_whenDeletingTagAsNormalUser() throws Exception {
        givenNormalUserSession();

        mockMvc.perform(post("/api/v1/tags/1/delete").header("Authorization", "Bearer " + USER_TOKEN))
                .andExpect(status().isForbidden());

        verify(tagGovernanceApplicationService, never()).deleteTag(anyLong(), any(), any());
    }
}
