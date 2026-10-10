package com.landray.skillhub.interfaces.rest.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.landray.skillhub.application.SkillApplicationService;
import com.landray.skillhub.application.SkillCertificationApplicationService;
import com.landray.skillhub.application.SkillClaimApplicationService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.application.upload.NewSkillCommand;
import com.landray.skillhub.application.upload.NewVersionCommand;
import com.landray.skillhub.application.upload.PrecheckResult;
import com.landray.skillhub.application.upload.SkillUploadApplicationService;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.CertificationDecision;
import com.landray.skillhub.domain.model.DeclaredOwner;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillVersionFileContent;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.OptionalLogin;
import com.landray.skillhub.interfaces.rest.auth.RequireAdmin;
import com.landray.skillhub.interfaces.rest.auth.RequireLogin;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 接口:读侧(含版本文件预览)匿名可用(业务建模文档 5.16 权限矩阵),写侧(precheck / 发布 / 改元数据 /
 * 上下架)一律 {@link RequireLogin},资源级的 owner/ADMIN 判断在 application 层完成(controller 不做业务判断)。
 *
 * <p><b>唯一的例外是认证</b>({@code /versions/{version}/certify}):那是平台治理动作,直接标
 * {@link RequireAdmin},不走"登录后再由 application 判 owner"那条路——认证不该对 owner 开放,理由见该方法注释。
 *
 * <p>薄控制器:只收参、调 application、转 DTO,不写业务判断、不碰 SQL(backend/CLAUDE.md 第一节)。
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillApplicationService skillApplicationService;
    private final SkillUploadApplicationService skillUploadApplicationService;
    private final SkillClaimApplicationService skillClaimApplicationService;
    private final SkillCertificationApplicationService skillCertificationApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public SkillController(
            SkillApplicationService skillApplicationService,
            SkillUploadApplicationService skillUploadApplicationService,
            SkillClaimApplicationService skillClaimApplicationService,
            SkillCertificationApplicationService skillCertificationApplicationService,
            CurrentUserAccessor currentUserAccessor) {
        this.skillApplicationService = skillApplicationService;
        this.skillUploadApplicationService = skillUploadApplicationService;
        this.skillClaimApplicationService = skillClaimApplicationService;
        this.skillCertificationApplicationService = skillCertificationApplicationService;
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

    /**
     * 管理视图列表:含已下架,给管理页用(owner 只看自己的,ADMIN 看全部)。
     *
     * <p><b>刻意不收 ownerUserId 参数。</b>作用域由服务端按当前身份强制决定 —— 收下这个参数就等于让客户端声明
     * "我要看谁的",而那是越权最常见的入口。路径放在 {@code /{id}} 之前,免得 {@code manage} 被当成 id 匹配掉。
     */
    @RequireLogin
    @GetMapping("/manage")
    public PageResult<ManagedSkillResponse> listManagedSkills(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        PageResult<Skill> result =
                skillApplicationService.listManagedSkills(actingUser, status, keyword, sort, page, size);
        return result.map(ManagedSkillResponse::from);
    }

    @GetMapping("/{id}")
    public SkillDetailResponse getSkill(@PathVariable long id) {
        Skill skill = skillApplicationService.getSkillDetail(id);
        return SkillDetailResponse.from(skill, skillApplicationService.getLatestVersionFileManifest(skill));
    }

    @GetMapping("/{id}/versions")
    public List<SkillVersionResponse> getVersions(@PathVariable long id) {
        return skillApplicationService.listVersions(id).stream()
                .map(SkillVersionResponse::from)
                .toList();
    }

    /**
     * 详情页文件树点开某个节点后的内容预览,匿名可用(与 {@link #getSkill} / {@link #getVersions} 同属读侧四个
     * 匿名可用接口的延伸——查看已发布制品的文件内容不涉及资源级权限判断)。{@code path} 是剥壳后的相对路径,
     * 由前端直接透传 {@code latestVersion.files[].path} 里的值。
     */
    @GetMapping("/{skillId}/versions/{versionId}/file")
    public SkillVersionFileContentResponse getVersionFileContent(
            @PathVariable long skillId, @PathVariable long versionId, @RequestParam String path) {
        SkillVersionFileContent result = skillApplicationService.getVersionFileContent(skillId, versionId, path);
        return SkillVersionFileContentResponse.from(result);
    }

    /**
     * precheck 匿名可用,但登录后结果不同。
     *
     * <p>上传已放开为匿名可用,precheck 是那条动作不可分割的一部分,自然也得匿名可用。但它的产出
     * {@code canPublishNewVersion} 依赖"当前是谁"(是不是目标 skill 的 owner 或 ADMIN),所以用
     * {@link OptionalLogin} 而不是完全不标注 —— 不标注的端点拦截器根本不解析会话,那个字段对已登录的
     * owner 也会恒为 false,等于废掉。匿名时它恒为 false:匿名只能发全新 skill,往已有 skill 追加版本
     * 属于管理既有资源,仍然要 owner / ADMIN。
     */
    @OptionalLogin
    @PostMapping("/precheck")
    public PrecheckResponse precheck(@RequestParam("file") MultipartFile file) {
        AuthenticatedUser actingUser = currentUserAccessor.current().orElse(null);
        PrecheckResult result = skillUploadApplicationService.precheck(readBytes(file), actingUser);
        return PrecheckResponse.from(result);
    }

    /**
     * 上传新 skill。**匿名可用**。
     *
     * <p>匿名上传的包落在内置 system 名下,即"无主待认领"({@code Skill#isUnclaimed()});此时可以通过
     * {@code declaredOwnerName} / {@code declaredOwnerEmployeeNo} 自己填一个归属,那是旁人判断"这大概是
     * 谁的包"的唯一线索,**未经核实**。登录后由 {@code POST /skills/{id}/claim} 认领,归属才真正落到一个
     * 账号上。
     *
     * <p>已登录时这两个参数会被拒(见 {@code DeclaredOwnerNotAllowedException}):归属人就是你本人,
     * 再带一份自称归属只会制造歧义。
     *
     * <p><b>代价要写在这里</b>:匿名上传意味着内网任何人都能往平台推送会被 Agent 执行的内容,且归属不可
     * 追溯。这是一个明确的产品取舍(放低投稿门槛),不是遗漏。平台侧仅剩的信任信号是认证(certify,
     * ADMIN 专属),所以前端必须对"无主 + 未认证"的包给出显著警示。
     */
    @OptionalLogin
    @PostMapping
    public SkillDetailResponse createSkill(
            @RequestParam("file") MultipartFile file,
            @RequestParam String version,
            @RequestParam(required = false) String displayName,
            @RequestParam String source,
            @RequestParam(required = false) List<Long> curatedTagIds,
            @RequestParam(required = false) List<String> freeTagNames,
            @RequestParam(required = false) String declaredOwnerName,
            @RequestParam(required = false) String declaredOwnerEmployeeNo) {
        // 匿名时归属人取内置 system(永远登不进来),于是这个 skill 天然处于待认领状态,
        // 且 owner/ADMIN 那条资源级权限判断对它自动退化成"仅 ADMIN",不需要单独写分支。
        long actingUserId = currentUserAccessor
                .current()
                .map(AuthenticatedUser::userId)
                .orElse(AppUser.SYSTEM_USER_ID);
        NewSkillCommand command = new NewSkillCommand(
                readBytes(file),
                version,
                displayName,
                source,
                curatedTagIds == null ? List.of() : curatedTagIds,
                freeTagNames == null ? List.of() : freeTagNames,
                actingUserId,
                DeclaredOwner.ofNullable(declaredOwnerName, declaredOwnerEmployeeNo));
        Skill skill = skillUploadApplicationService.publishNewSkill(command);
        return SkillDetailResponse.from(skill);
    }

    /**
     * 认领一个无主 skill(匿名上传或早先导入的包),归属落到当前登录人。
     *
     * <p>先到先得,刻意不设资格门槛:声明归属里的工号是上传者自称的、未经核实,而本地建号普遍没绑工号,
     * 拿它当门槛的实际效果是声明了工号的包谁都认领不了。换来的代价("可以认领别人匿名传的包")用审计留痕
     * 加 ADMIN 可改派来兜。并发下的先后由仓储的比较并交换仲裁,不靠先查后改。
     */
    @RequireLogin
    @PostMapping("/{id}/claim")
    public SkillDetailResponse claimSkill(@PathVariable long id) {
        Skill skill = skillClaimApplicationService.claim(id, currentUserAccessor.require());
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

    /**
     * 物理删除,不可逆(owner / ADMIN)。
     *
     * <p>与上下架不同,这里<b>不能返回 SkillDetailResponse</b> —— 资源已经不存在了,再回一份它的详情是在描述
     * 一个不存在的东西。返回 204,让"没有内容"这件事由状态码说清楚。
     */
    @RequireLogin
    @PostMapping("/{id}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable long id) {
        AuthenticatedUser actingUser = currentUserAccessor.require();
        skillApplicationService.delete(id, actingUser);
    }

    /**
     * 认证或撤销某个版本(业务建模文档 5.8、6.2)。
     *
     * <p>本类里唯一标 {@link RequireAdmin} 的端点:其余写动作都是 owner / ADMIN 都能做,资源级判断落在
     * application 层;而认证是纯平台治理动作,连"是不是自己的 skill"都不该放行——作者给自己发认证徽章
     * 会让整个认证机制当场归零。
     *
     * <p>路径参数是<b>版本号</b>而不是 versionId,与下载接口一致:版本号是人看得懂、贴在群里也不会指错的标识。
     */
    @RequireAdmin
    @PostMapping("/{id}/versions/{version}/certify")
    public SkillVersionResponse certifyVersion(
            @PathVariable long id, @PathVariable String version, @Valid @RequestBody CertifyVersionRequest request) {
        AuthenticatedUser admin = currentUserAccessor.require();
        CertificationDecision decision = CertificationDecision.of(request.certification(), request.note());
        return SkillVersionResponse.from(
                skillCertificationApplicationService.certify(id, version, decision, admin));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传的 skill 包失败", e);
        }
    }
}
