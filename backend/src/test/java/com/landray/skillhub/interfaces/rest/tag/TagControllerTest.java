package com.landray.skillhub.interfaces.rest.tag;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.TagApplicationService;
import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagWithSkillCount;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;

/**
 * {@code @WebMvcTest} + MockMvc,mock 掉 application 层。能不 mock 整个 MyBatis 层就跑起来的理由见
 * {@code SkillControllerTest} 类注释——两者是同一个问题(Mapper 扫描配置的位置)。
 */
@WebMvcTest(controllers = TagController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
@Import(CurrentUserAccessor.class)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagApplicationService tagApplicationService;

    /**
     * {@code AuthenticationInterceptor} 是 {@code HandlerInterceptor},属于 {@code @WebMvcTest} 切片<b>一定会
     * 纳入</b>的 web 层类型,它的依赖不给就整个上下文起不来 —— 与本接口是否需要登录无关(本接口匿名可访问)。
     */
    @MockitoBean
    private AuthApplicationService authApplicationService;

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
}
