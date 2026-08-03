package com.landray.skillhub.interfaces.rest.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.landray.skillhub.application.SkillApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.application.upload.NewSkillCommand;
import com.landray.skillhub.application.upload.NewVersionCommand;
import com.landray.skillhub.application.upload.PrecheckResult;
import com.landray.skillhub.application.upload.SkillUploadApplicationService;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.RequireLogin;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 接口:读侧四个匿名可用(业务建模文档 5.16 权限矩阵),写侧(precheck / 发布 / 改元数据 / 上下架)一律
 * {@link RequireLogin},资源级的 owner/ADMIN 判断在 application 层完成(controller 不做业务判断)。
 *
 * <p>薄控制器:只收参、调 application、转 DTO,不写业务判断、不碰 SQL(backend/CLAUDE.md 第一节)。
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillApplicationService skillApplicationService;
    private final SkillUploadApplicationService skillUploadApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public SkillController(
            SkillApplicationService skillApplicationService,
            SkillUploadApplicationService skillUploadApplicationService,
            CurrentUserAccessor currentUserAccessor) {
        this.skillApplicationService = skillApplicationService;
        this.skillUploadApplicationService = skillUploadApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public PageResult<SkillSummaryResponse> listSkills(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(defaultValue = "false") boolean certifiedOnly,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageResult<Skill> result = skillApplicationService.listSkills(
                source, tagIds, certifiedOnly, keyword, ownerUserId, sort, page, size);
        return result.map(SkillSummaryResponse::from);
    }

    @GetMapping("/{id}")
    public SkillDetailResponse getSkill(@PathVariable long id) {
        Skill skill = skillApplicationService.getSkillDetail(id);
        return SkillDetailResponse.from(skill);
    }

    @GetMapping("/{id}/versions")
    public List<SkillVersionResponse> getVersions(@PathVariable long id) {
        return skillApplicationService.listVersions(id).stream()
                .map(SkillVersionResponse::from)
                .toList();
    }

    /**
     * precheck 要求登录:它的产出({@code canPublishNewVersion} / {@code suggestedName})依赖"当前是谁",而
     * "上传新 skill"这条业务动作本身匿名不可用(业务建模文档 5.16),precheck 是这条动作不可分割的一部分。
     */
    @RequireLogin
    @PostMapping("/precheck")
    public PrecheckResponse precheck(@RequestParam("file") MultipartFile file) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        PrecheckResult result = skillUploadApplicationService.precheck(readBytes(file), actingUser);
        return PrecheckResponse.from(result);
    }

    @RequireLogin
    @PostMapping
    public SkillDetailResponse createSkill(
            @RequestParam("file") MultipartFile file,
            @RequestParam String version,
            @RequestParam(required = false) String displayName,
            @RequestParam String source,
            @RequestParam(required = false) List<Long> curatedTagIds,
            @RequestParam(required = false) List<String> freeTagNames) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        NewSkillCommand command = new NewSkillCommand(
                readBytes(file),
                version,
                displayName,
                source,
                curatedTagIds == null ? List.of() : curatedTagIds,
                freeTagNames == null ? List.of() : freeTagNames,
                actingUser.userId());
        Skill skill = skillUploadApplicationService.publishNewSkill(command);
        return SkillDetailResponse.from(skill);
    }

    /** owner / ADMIN 判断在 {@code SkillUploadApplicationService} 内(资源级权限,{@code @RequireLogin} 管不到)。 */
    @RequireLogin
    @PostMapping("/{id}/versions")
    public SkillDetailResponse createVersion(
            @PathVariable long id, @RequestParam("file") MultipartFile file, @RequestParam String version) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        NewVersionCommand command = new NewVersionCommand(readBytes(file), version, actingUser);
        Skill skill = skillUploadApplicationService.publishNewVersion(id, command);
        return SkillDetailResponse.from(skill);
    }

    @RequireLogin
    @PostMapping("/{id}/update")
    public SkillDetailResponse updateSkill(@PathVariable long id, @Valid @RequestBody UpdateSkillRequest request) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        Skill skill = skillApplicationService.updateMetadata(
                id,
                request.displayName(),
                request.source(),
                request.curatedTagIds(),
                request.freeTagNames() == null ? List.of() : request.freeTagNames(),
                actingUser);
        return SkillDetailResponse.from(skill);
    }

    @RequireLogin
    @PostMapping("/{id}/unlist")
    public SkillDetailResponse unlistSkill(@PathVariable long id) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        return SkillDetailResponse.from(skillApplicationService.unlist(id, actingUser));
    }

    @RequireLogin
    @PostMapping("/{id}/relist")
    public SkillDetailResponse relistSkill(@PathVariable long id) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        return SkillDetailResponse.from(skillApplicationService.relist(id, actingUser));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传的 skill 包失败", e);
        }
    }
}
