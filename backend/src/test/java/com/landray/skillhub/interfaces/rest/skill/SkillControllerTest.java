package com.landray.skillhub.interfaces.rest.skill;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.landray.skillhub.application.SkillApplicationService;
import com.landray.skillhub.application.auth.AuthApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.application.upload.PrecheckResult;
import com.landray.skillhub.application.upload.SkillUploadApplicationService;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.NotPreviewableReason;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.SkillVersionFile;
import com.landray.skillhub.domain.model.SkillVersionFileContent;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.interfaces.rest.auth.AuthenticationInterceptor;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.WebMvcAuthConfig;
import com.landray.skillhub.shared.exception.AccessDeniedException;
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
 *
 * <p>写侧端点(precheck / 发布 / 改元数据 / 上下架)都标了 {@code @RequireLogin},需要
 * {@code AuthenticationInterceptor} 真的生效才能测到 401/403 分支,所以额外 {@code @Import}
 * {@link AuthenticationInterceptor} 与 {@link WebMvcAuthConfig}(与 {@code UserControllerTest} 同一套模式)。
 */
@WebMvcTest(controllers = SkillController.class)
@TestPropertySource(properties = "server.servlet.context-path=")
@Import({AuthenticationInterceptor.class, WebMvcAuthConfig.class, CurrentUserAccessor.class})
class SkillControllerTest {

    private static final String VALID_TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillApplicationService skillApplicationService;

    @MockitoBean
    private SkillUploadApplicationService skillUploadApplicationService;

    /**
     * 这四个只读接口是匿名可访问的(业务建模文档 5.16 权限矩阵)。但 {@code AuthenticationInterceptor}
     * 是 {@code HandlerInterceptor},属于 {@code @WebMvcTest} 切片<b>一定会纳入</b>的 web 层类型,它的依赖不给就
     * 整个上下文起不来。**每一个新的 Controller 切片测试都要带上这一行**,与该 Controller 是否需要登录无关。
     */
    @MockitoBean
    private AuthApplicationService authApplicationService;

    private void stubValidSession(long userId, UserRole role) {
        when(authApplicationService.resolveSession(VALID_TOKEN))
                .thenReturn(Optional.of(new AuthenticatedUser(userId, role, 10L, 99L)));
    }

    private Skill sampleSkill(SkillStatus status) {
        SkillVersion latestVersion = new SkillVersion(
                10L, 1L, "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), "0".repeat(64), 1024L, 3,
                List.of("Read", "Grep"), "# skill\n正文", "# readme", null, Certification.CERTIFIED, 1L,
                Instant.parse("2026-01-01T00:00:00Z"), null, 2L, Instant.parse("2026-01-01T00:00:00Z"));
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
    @DisplayName("详情接口的 latestVersion.files 来自 application 层现读的文件清单")
    void should_returnLatestVersionFiles_whenManifestAvailable() throws Exception {
        when(skillApplicationService.getSkillDetail(1L)).thenReturn(sampleSkill(SkillStatus.PUBLISHED));
        when(skillApplicationService.getLatestVersionFileManifest(any()))
                .thenReturn(List.of(
                        new SkillVersionFile("SKILL.md", 1234L), new SkillVersionFile("references/guide.md", 567L)));

        mockMvc.perform(get("/api/v1/skills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestVersion.files[0].path").value("SKILL.md"))
                .andExpect(jsonPath("$.latestVersion.files[0].sizeBytes").value(1234))
                .andExpect(jsonPath("$.latestVersion.files[1].path").value("references/guide.md"));
    }

    @Test
    @DisplayName("application 层文件清单为空时,详情接口返回空数组而不是省略字段")
    void should_returnEmptyFilesArray_whenManifestIsEmpty() throws Exception {
        when(skillApplicationService.getSkillDetail(1L)).thenReturn(sampleSkill(SkillStatus.PUBLISHED));
        when(skillApplicationService.getLatestVersionFileManifest(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/skills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestVersion.files").isArray())
                .andExpect(jsonPath("$.latestVersion.files").isEmpty());
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
                10L, 1L, "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), "0".repeat(64), 1024L, 3, List.of(), null,
                null, null, Certification.REVOKED, 1L, Instant.parse("2026-01-01T00:00:00Z"), "存在安全隐患", 2L,
                Instant.parse("2026-01-01T00:00:00Z"));
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

    // ------------------------------------------------------------------
    // 版本文件预览:GET /skills/{skillId}/versions/{versionId}/file
    // ------------------------------------------------------------------

    @Test
    @DisplayName("文件预览:可预览时返回解码后的正文,query 参数原样透传给 application 层")
    void should_returnFileContent_whenFilePreviewable() throws Exception {
        when(skillApplicationService.getVersionFileContent(1L, 10L, "references/guide.md"))
                .thenReturn(SkillVersionFileContent.previewable("references/guide.md", 5L, "guide"));

        mockMvc.perform(get("/api/v1/skills/1/versions/10/file").param("path", "references/guide.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("references/guide.md"))
                .andExpect(jsonPath("$.sizeBytes").value(5))
                .andExpect(jsonPath("$.previewable").value(true))
                .andExpect(jsonPath("$.content").value("guide"))
                .andExpect(jsonPath("$.notPreviewableReason").doesNotExist());

        verify(skillApplicationService).getVersionFileContent(1L, 10L, "references/guide.md");
    }

    @Test
    @DisplayName("文件预览:不可预览时 content 为空,notPreviewableReason 携带原因")
    void should_returnNotPreviewable_whenFileIsBinary() throws Exception {
        when(skillApplicationService.getVersionFileContent(1L, 10L, "assets/logo.png"))
                .thenReturn(SkillVersionFileContent.notPreviewable("assets/logo.png", 2048L, NotPreviewableReason.BINARY));

        mockMvc.perform(get("/api/v1/skills/1/versions/10/file").param("path", "assets/logo.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewable").value(false))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.notPreviewableReason").value("BINARY"));
    }

    @Test
    @DisplayName("文件预览:application 层因文件不存在抛出 404 时原样透传")
    void should_return404_whenVersionFileNotFound() throws Exception {
        when(skillApplicationService.getVersionFileContent(anyLong(), anyLong(), any()))
                .thenThrow(new ResourceNotFoundException("SKILL_VERSION_FILE_NOT_FOUND", "文件不存在, skillId=1, versionId=10"));

        mockMvc.perform(get("/api/v1/skills/1/versions/10/file").param("path", "not-exist.md"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SKILL_VERSION_FILE_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // precheck / 发布 / 改元数据 / 上下架——写侧,都要求登录
    // ------------------------------------------------------------------

    @Test
    @DisplayName("precheck 未带 token 时返回 401")
    void should_returnUnauthorized_when_precheckCalledWithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", "dummy".getBytes());

        mockMvc.perform(multipart("/api/v1/skills/precheck").file(file)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("precheck 登录后返回 application 层算出的结果")
    void should_returnPrecheckResult_when_tokenValid() throws Exception {
        stubValidSession(9L, UserRole.USER);
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", "dummy".getBytes());
        PrecheckResult result = new PrecheckResult(
                true,
                new PrecheckResult.ParsedInfo("foo", "描述", List.of("Read")),
                "d".repeat(64),
                3,
                2048L,
                true,
                null,
                false,
                null,
                null,
                List.of());
        when(skillUploadApplicationService.precheck(any(), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/skills/precheck").file(file).header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.parsed.name").value("foo"))
                .andExpect(jsonPath("$.contentDigest").value("d".repeat(64)))
                .andExpect(jsonPath("$.duplicatedName").value(false));
    }

    @Test
    @DisplayName("precheck 校验不通过时仍返回 200,携带 valid=false 与 violations")
    void should_returnInvalidPrecheckResult_when_packageFailsValidation() throws Exception {
        stubValidSession(9L, UserRole.USER);
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", "dummy".getBytes());
        PrecheckResult invalid = PrecheckResult.invalid("SKILL_PACKAGE_STRUCTURE_INVALID", "包内缺少顶层 SKILL.md");
        when(skillUploadApplicationService.precheck(any(), any())).thenReturn(invalid);

        mockMvc.perform(multipart("/api/v1/skills/precheck").file(file).header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.violations[0].code").value("SKILL_PACKAGE_STRUCTURE_INVALID"));
    }

    @Test
    @DisplayName("创建 skill 未带 token 时返回 401")
    void should_returnUnauthorized_when_createSkillCalledWithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", "dummy".getBytes());

        mockMvc.perform(multipart("/api/v1/skills").file(file).param("version", "1.0.0").param("source", "internal"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("创建 skill:登录后调用 application 层,返回详情")
    void should_createSkill_when_tokenValid() throws Exception {
        stubValidSession(9L, UserRole.USER);
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", "dummy".getBytes());
        when(skillUploadApplicationService.publishNewSkill(any())).thenReturn(sampleSkill(SkillStatus.PUBLISHED));

        mockMvc.perform(multipart("/api/v1/skills")
                        .file(file)
                        .param("version", "1.0.0")
                        .param("displayName", "展示名")
                        .param("source", "internal")
                        .param("curatedTagIds", "1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("code-review"));
    }

    @Test
    @DisplayName("发布新版本:登录后调用 application 层")
    void should_createVersion_when_tokenValid() throws Exception {
        stubValidSession(9L, UserRole.USER);
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", "dummy".getBytes());
        when(skillUploadApplicationService.publishNewVersion(eq(1L), any()))
                .thenReturn(sampleSkill(SkillStatus.PUBLISHED));

        mockMvc.perform(multipart("/api/v1/skills/1/versions")
                        .file(file)
                        .param("version", "1.1.0")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("code-review"));
    }

    @Test
    @DisplayName("发布新版本:application 层因非 owner/ADMIN 拒绝时返回 403")
    void should_returnForbidden_when_createVersionRejectedByApplicationLayer() throws Exception {
        stubValidSession(999L, UserRole.USER);
        MockMultipartFile file = new MockMultipartFile("file", "skill.zip", "application/zip", "dummy".getBytes());
        when(skillUploadApplicationService.publishNewVersion(anyLong(), any()))
                .thenThrow(new AccessDeniedException("只有 owner 或 ADMIN 能给这个 skill 发布新版本"));

        mockMvc.perform(multipart("/api/v1/skills/1/versions")
                        .file(file)
                        .param("version", "1.1.0")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("改元数据未带 token 时返回 401")
    void should_returnUnauthorized_when_updateSkillCalledWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/skills/1/update")
                        .contentType("application/json")
                        .content("{\"displayName\":\"新展示名\",\"source\":\"internal\",\"curatedTagIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("改元数据:登录后调用 application 层")
    void should_updateSkill_when_tokenValid() throws Exception {
        stubValidSession(2L, UserRole.USER);
        when(skillApplicationService.updateMetadata(eq(1L), eq("新展示名"), eq("internal"), eq(List.of(1L)), any(), any()))
                .thenReturn(sampleSkill(SkillStatus.PUBLISHED));

        mockMvc.perform(post("/api/v1/skills/1/update")
                        .contentType("application/json")
                        .content("{\"displayName\":\"新展示名\",\"source\":\"internal\",\"curatedTagIds\":[1]}")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("code-review"));
    }

    @Test
    @DisplayName("改元数据:application 层因非 owner/ADMIN 拒绝时返回 403")
    void should_returnForbidden_when_updateSkillRejectedByApplicationLayer() throws Exception {
        stubValidSession(999L, UserRole.USER);
        when(skillApplicationService.updateMetadata(anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new AccessDeniedException("只有 owner 或 ADMIN 能操作这个 skill"));

        mockMvc.perform(post("/api/v1/skills/1/update")
                        .contentType("application/json")
                        .content("{\"displayName\":\"新展示名\",\"source\":\"internal\",\"curatedTagIds\":[1]}")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("改元数据:displayName 为空时返回 400")
    void should_returnBadRequest_when_updateSkillWithBlankDisplayName() throws Exception {
        stubValidSession(2L, UserRole.USER);

        mockMvc.perform(post("/api/v1/skills/1/update")
                        .contentType("application/json")
                        .content("{\"displayName\":\"\",\"source\":\"internal\",\"curatedTagIds\":[1]}")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("下架:登录后调用 application 层")
    void should_unlistSkill_when_tokenValid() throws Exception {
        stubValidSession(2L, UserRole.USER);
        when(skillApplicationService.unlist(eq(1L), any())).thenReturn(sampleSkill(SkillStatus.UNLISTED));

        mockMvc.perform(post("/api/v1/skills/1/unlist").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNLISTED"));
    }

    @Test
    @DisplayName("恢复:登录后调用 application 层")
    void should_relistSkill_when_tokenValid() throws Exception {
        stubValidSession(2L, UserRole.USER);
        when(skillApplicationService.relist(eq(1L), any())).thenReturn(sampleSkill(SkillStatus.PUBLISHED));

        mockMvc.perform(post("/api/v1/skills/1/relist").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("管理视图:/skills/manage 不会被 /skills/{id} 抢走——字面量路径段优先于路径变量")
    void should_routeToManageEndpoint_when_pathIsManage() throws Exception {
        // 这条不是多余的:两个映射的第一段都是 /skills,第二段一个是字面量一个是变量。
        // 匹配优先级搞错的话表现是 400(把 "manage" 当成 id 解析失败),而那个报错完全不指向路由。
        stubValidSession(2L, UserRole.USER);
        PageResult<Skill> page = new PageResult<>(List.of(sampleSkill(SkillStatus.UNLISTED)), 1L, 1, 20);
        when(skillApplicationService.listManagedSkills(any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/skills/manage").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("UNLISTED"));
    }

    @Test
    @DisplayName("管理视图:未登录直接 401——已下架内容不能被匿名枚举")
    void should_rejectManageList_when_tokenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/skills/manage")).andExpect(status().isUnauthorized());

        verify(skillApplicationService, never())
                .listManagedSkills(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("删除:登录后调用 application 层,返回 204 而不是一份已不存在资源的详情")
    void should_deleteSkill_when_tokenValid() throws Exception {
        stubValidSession(2L, UserRole.USER);

        mockMvc.perform(post("/api/v1/skills/1/delete").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(skillApplicationService).delete(eq(1L), any());
    }

    @Test
    @DisplayName("删除:未登录直接 401,不进 application 层")
    void should_rejectDelete_when_tokenMissing() throws Exception {
        mockMvc.perform(post("/api/v1/skills/1/delete")).andExpect(status().isUnauthorized());

        // 资源级的 owner/ADMIN 判断在 application 层,但"登录了没有"必须在进去之前就拦下 ——
        // 否则未登录请求也会走到删除逻辑里,只是最后被另一处拒绝,白白多一层暴露面。
        verify(skillApplicationService, never()).delete(anyLong(), any());
    }
}
