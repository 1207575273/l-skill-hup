package com.landray.skillhub.interfaces.rest.skill;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.SkillApplicationService;
import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.model.PageResult;

/**
 * {@code @WebMvcTest} + MockMvc:只验参数绑定、状态码、错误映射,mock 掉 application 层(backend/CLAUDE.md 8.1)。
 *
 * <p>{@code server.servlet.context-path} 清空的理由与 {@code GlobalExceptionHandlerTest} 一致——切片测试仍会加载
 * {@code application-dev.yml},清空后测试路径不用带 {@code /skillhub} 前缀。
 *
 * <p>能不 mock 掉整个 MyBatis 层就跑起来,依赖 {@code @MapperScan} 被放在独立的
 * {@link com.landray.skillhub.infrastructure.config.MyBatisMapperScanConfig} 而不是
 * {@code SkillHubApplication} 上——后者会被 {@code @WebMvcTest} 当作配置源加载,若 {@code @MapperScan}
 * 直接挂在它身上,Mapper 接口会被扫描成 bean,却因为切片测试里没有 {@code SqlSessionFactory} 而启动失败
 * (实测过,且这类失败连 {@code excludeAutoConfiguration} 排除 {@code MybatisAutoConfiguration} 都救不了,
 * 因为 {@code @MapperScan} 是显式 {@code @Import} 注册,不受自动配置条件评估管辖)。详见该配置类的类注释。
 */
@WebMvcTest(controllers = SkillController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
@Import(CurrentUserAccessor.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillApplicationService skillApplicationService;

    /**
     * 这四个接口是匿名可访问的(业务建模文档 5.16 权限矩阵),本测试也不验鉴权。但 {@code AuthenticationInterceptor}
     * 是 {@code HandlerInterceptor},属于 {@code @WebMvcTest} 切片<b>一定会纳入</b>的 web 层类型,它的依赖不给就
     * 整个上下文起不来。**每一个新的 Controller 切片测试都要带上这一行**,与该 Controller 是否需要登录无关。
     */
    @MockitoBean
    private AuthApplicationService authApplicationService;

    private Skill sampleSkill(SkillStatus status) {
        SkillVersion latestVersion = new SkillVersion(
                10L, 1L, "1.0.0", 1024L, 3, List.of("Read", "Grep"), "# skill\n正文", "# readme", null,
                Certification.CERTIFIED, 1L, Instant.parse("2026-01-01T00:00:00Z"), null, 2L,
                Instant.parse("2026-01-01T00:00:00Z"));
        return new Skill(
                1L,
                new SkillName("code-review"),
                "代码审查助手",
                "一句话描述",
                SkillSource.INTERNAL,
                new SkillOwner(2L, "张三", "80380486"),
                "卡片摘要",
                status,
                100L,
                10,
                1,
                5,
                2,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                latestVersion,
                List.of());
    }

    @Test
    @DisplayName("列表接口返回分页结构 { items, total, page, size }")
    void should_returnPagedStructure_whenListingSkills() throws Exception {
        PageResult<Skill> page = new PageResult<>(List.of(sampleSkill(SkillStatus.PUBLISHED)), 1L, 1, 20);
        when(skillApplicationService.listSkills(
                        isNull(), isNull(), eq(false), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].name").value("code-review"))
                .andExpect(jsonPath("$.items[0].certification").value("CERTIFIED"))
                .andExpect(jsonPath("$.items[0].owner.employeeNo").value("80380486"));
    }

    @Test
    @DisplayName("查询参数原样透传给 application 层")
    void should_passQueryParamsThrough_whenListingSkills() throws Exception {
        when(skillApplicationService.listSkills(any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0L, 2, 5));

        mockMvc.perform(get("/api/v1/skills")
                        .param("source", "internal")
                        .param("tagIds", "1", "2")
                        .param("certifiedOnly", "true")
                        .param("keyword", "审查")
                        .param("ownerUserId", "9")
                        .param("sort", "downloads")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(skillApplicationService)
                .listSkills("internal", List.of(1L, 2L), true, "审查", 9L, "downloads", 2, 5);
    }

    @Test
    @DisplayName("详情接口返回完整字段,不含表态/收藏(P2 范围外)")
    void should_returnSkillDetail_whenSkillFound() throws Exception {
        when(skillApplicationService.getSkillDetail(1L)).thenReturn(sampleSkill(SkillStatus.PUBLISHED));

        mockMvc.perform(get("/api/v1/skills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("code-review"))
                .andExpect(jsonPath("$.latestVersion.skillMdContent").value("# skill\n正文"))
                .andExpect(jsonPath("$.latestVersion.readmeContent").value("# readme"))
                .andExpect(jsonPath("$.latestVersion.allowedTools[0]").value("Read"))
                .andExpect(jsonPath("$.reaction").doesNotExist())
                .andExpect(jsonPath("$.favorited").doesNotExist());
    }

    @Test
    @DisplayName("已下架的 skill 通过详情接口仍可访问,status 字段如实反映 UNLISTED")
    void should_returnUnlistedStatus_whenDetailIsUnlisted() throws Exception {
        when(skillApplicationService.getSkillDetail(1L)).thenReturn(sampleSkill(SkillStatus.UNLISTED));

        mockMvc.perform(get("/api/v1/skills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNLISTED"));
    }

    @Test
    @DisplayName("skill 不存在时详情接口返回 404")
    void should_return404_whenSkillNotFound() throws Exception {
        when(skillApplicationService.getSkillDetail(anyLong()))
                .thenThrow(new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在, id=999"));

        mockMvc.perform(get("/api/v1/skills/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKILL_NOT_FOUND"));
    }

    @Test
    @DisplayName("版本列表接口返回每个版本的认证状态")
    void should_returnVersionList_whenListingVersions() throws Exception {
        SkillVersion version = new SkillVersion(
                10L, 1L, "1.0.0", 1024L, 3, List.of(), null, null, null, Certification.REVOKED, 1L,
                Instant.parse("2026-01-01T00:00:00Z"), "存在安全隐患", 2L, Instant.parse("2026-01-01T00:00:00Z"));
        when(skillApplicationService.listVersions(1L)).thenReturn(List.of(version));

        mockMvc.perform(get("/api/v1/skills/1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value("1.0.0"))
                .andExpect(jsonPath("$[0].certification").value("REVOKED"))
                .andExpect(jsonPath("$[0].certifyNote").value("存在安全隐患"));
    }

    @Test
    @DisplayName("版本列表接口:skill 不存在时返回 404")
    void should_return404_whenListingVersionsForMissingSkill() throws Exception {
        when(skillApplicationService.listVersions(anyLong()))
                .thenThrow(new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在, id=999"));

        mockMvc.perform(get("/api/v1/skills/999/versions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKILL_NOT_FOUND"));
    }
}
