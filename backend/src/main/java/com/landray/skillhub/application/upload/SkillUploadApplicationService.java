package com.landray.skillhub.application.upload;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.application.SkillTagAssignmentService;
import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.exception.SkillVersionNameMismatchException;
import com.landray.skillhub.domain.exception.SkillVersionNotIncreasingException;
import com.landray.skillhub.domain.model.CardExcerptGenerator;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.ParsedSkillPackage;
import com.landray.skillhub.domain.model.RepackagedArtifact;
import com.landray.skillhub.domain.model.SemVer;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.domain.service.SkillPackageValidator;
import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.BusinessException;
import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

/**
 * skill 上传管线的用例编排:precheck、发布新 skill、发布新版本(业务建模文档 5.1~5.5)。事务边界只在这一层
 * (backend/CLAUDE.md 第五节)。
 *
 * <p><b>precheck 与正式发布共用同一个 {@link SkillPackageValidator} 实例</b>——第 1~11 步的校验/重新打包逻辑
 * 只写一份,唯一的差别是本类怎么处理校验失败:precheck 捕获后转成 {@code valid:false} 的响应体(它的职责是"给
 * 前端反馈,不落库"),正式发布让异常正常传播成 4xx(GlobalExceptionHandler 统一处理)。
 */
@Service
public class SkillUploadApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillUploadApplicationService.class);

    /** 短哈希升位顺序:6 -> 8 -> 10 -> 12 位,与业务建模文档 5.5 一致。 */
    private static final int[] SUGGESTION_PREFIX_LENGTHS = {6, 8, 10, 12};

    private static final String SKILL_NOT_FOUND_CODE = "SKILL_NOT_FOUND";

    private final SkillPackageValidator validator;
    private final SkillRepository skillRepository;
    private final ArtifactStorage artifactStorage;
    private final SkillTagAssignmentService tagAssignmentService;
    private final AuditLog auditLog;

    public SkillUploadApplicationService(
            UploadProperties uploadProperties,
            SkillRepository skillRepository,
            ArtifactStorage artifactStorage,
            SkillTagAssignmentService tagAssignmentService,
            AuditLog auditLog) {
        this.validator = new SkillPackageValidator(uploadProperties.toLimits());
        this.skillRepository = skillRepository;
        this.artifactStorage = artifactStorage;
        this.tagAssignmentService = tagAssignmentService;
        this.auditLog = auditLog;
    }

    /**
     * precheck:只读,不落库(业务建模文档 5.1)。校验失败时不抛异常,转成 {@code valid=false} 的结果——
     * 前端需要在一次请求里看到"为什么不行",而不是拿到一个 400 就结束交互。
     */
    @Transactional(readOnly = true)
    public PrecheckResult precheck(byte[] zipBytes, AuthenticatedUser actingUser) {
        ParsedSkillPackage parsed;
        try {
            parsed = validator.parseAndValidate(zipBytes);
        } catch (BusinessException e) {
            log.warn("precheck 未通过, code={}, message={}", e.code(), e.getMessage());
            return PrecheckResult.invalid(e.code(), e.getMessage());
        }

        PrecheckResult.IdenticalMatch identical = skillRepository
                .findVersionByContentDigest(parsed.contentDigest())
                .map(version -> new PrecheckResult.IdenticalMatch(version.skillId(), version.version()))
                .orElse(null);

        Optional<Skill> existingSkill = skillRepository.findByName(parsed.name());
        if (existingSkill.isEmpty()) {
            return new PrecheckResult(
                    true,
                    new PrecheckResult.ParsedInfo(parsed.name(), parsed.description(), parsed.allowedTools()),
                    parsed.contentDigest(),
                    parsed.fileCount(),
                    parsed.unpackedBytes(),
                    parsed.hasReadme(),
                    identical,
                    false,
                    null,
                    null,
                    List.of());
        }

        Skill existing = existingSkill.get();
        boolean canPublishNewVersion =
                actingUser != null && (actingUser.isAdmin() || actingUser.userId() == existing.owner().userId());
        PrecheckResult.ExistingSkillInfo existingInfo = new PrecheckResult.ExistingSkillInfo(
                existing.id(),
                existing.name().value(),
                existing.owner().displayName(),
                existing.owner().employeeNo(),
                existing.latestVersion() == null ? null : existing.latestVersion().version(),
                canPublishNewVersion);

        return new PrecheckResult(
                true,
                new PrecheckResult.ParsedInfo(parsed.name(), parsed.description(), parsed.allowedTools()),
                parsed.contentDigest(),
                parsed.fileCount(),
                parsed.unpackedBytes(),
                parsed.hasReadme(),
                identical,
                true,
                existingInfo,
                suggestName(parsed.name(), parsed.contentDigest()),
                List.of());
    }

    /**
     * 发布新 skill + 首个版本(业务建模文档 5.1 的"改名新建"分支)。
     *
     * <p>最终 name 的确定方式:先用 {@link #resolveFinalNameForNewSkill} 做一轮 best-effort 的
     * {@code findByName} 预选(6/8/10/12 位短哈希逐级升位),再用预选出的名字做<b>一次</b>真正的插入。
     * <b>刻意不在数据库事务内对撞了唯一约束的候选名做"捕获后重试下一位"</b>——PostgreSQL
     * 一旦某条语句违反约束,整个事务会被标记为 aborted,后续语句(包括换个名字重新 INSERT)在
     * {@code ROLLBACK} 之前全部报 {@code current transaction is aborted}，要绕开这一点得靠嵌套事务
     * /SAVEPOINT，复杂度与收益不成正比（这是双方同一时刻上传字节完全相同的内容这一非常小的并发窗口）。
     * 真发生这种极端并发,插入报 {@code SKILL_NAME_DUPLICATED} 后由客户端重新发起一次上传即可——那时
     * {@code findByName} 会看到最新状态,不会无限循环。唯一约束仍然是最终仲裁者,数据不会因此出现重复 name
     * (业务建模文档 5.14 的核心诉求)。
     */
    @Transactional
    public Skill publishNewSkill(NewSkillCommand command) {
        ParsedSkillPackage parsed = validator.parseAndValidate(command.zipBytes());
        SemVer version = SemVer.parse(command.version());
        SkillSource source = SkillSource.fromParam(command.source());
        List<Long> tagIds = tagAssignmentService.resolveTagIds(
                command.curatedTagIds(), command.freeTagNames(), command.actingUserId());

        SkillName finalName = resolveFinalNameForNewSkill(parsed);
        RepackagedArtifact artifact = validator.repackage(parsed, finalName);
        String cardExcerpt = computeCardExcerpt(parsed, artifact);
        String artifactKey = buildArtifactKey(finalName, version, artifact.sha256());
        String displayName =
                command.displayName() == null || command.displayName().isBlank()
                        ? finalName.value()
                        : command.displayName().trim();

        artifactStorage.store(artifactKey, artifact.zipBytes());

        long skillId = skillRepository.insertSkill(
                finalName, displayName, parsed.description(), source, command.actingUserId(), cardExcerpt);
        long versionId = skillRepository.insertVersion(newVersionDraft(
                skillId, version, artifactKey, parsed, artifact, command.actingUserId()));

        tagAssignmentService.replaceSkillTags(skillId, tagIds);
        skillRepository.updateLatestVersionIfGreater(
                skillId, versionId, version.raw(), parsed.description(), cardExcerpt);

        auditLog.info(
                "skill 上传完成, event={}, skillId={}, skillName={}, skillVersion={}, operator={}",
                "skill_uploaded",
                skillId,
                finalName.value(),
                version.raw(),
                command.actingUserId());

        return requireSkill(skillId);
    }

    /**
     * 发布新版本(业务建模文档 5.1 的"作为新版本"分支)。目标 skill 的 name 固定,不接受改名。
     *
     * <p>owner / ADMIN 校验(业务建模文档 5.16)是资源级权限,取决于"这是谁的 skill",{@code @RequireLogin}
     * 只能确认登录与否,判断不了这一层,必须在这里做——漏了这一条,任何登录用户都能给别人的 skill 发新版本。
     */
    @Transactional
    public Skill publishNewVersion(long skillId, NewVersionCommand command) {
        Skill skill = skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
        requireOwnerOrAdmin(skill, command.actingUser());

        ParsedSkillPackage parsed = validator.parseAndValidate(command.zipBytes());
        if (!parsed.name().equals(skill.name().value())) {
            throw new SkillVersionNameMismatchException("包内 frontmatter 的 name(" + parsed.name()
                    + ")与目标 skill 的 name(" + skill.name().value() + ")不一致");
        }

        SemVer newVersion = SemVer.parse(command.version());
        Optional<String> currentMax = skillRepository.findLatestVersionString(skillId);
        if (currentMax.isPresent() && !newVersion.isGreaterThan(SemVer.parse(currentMax.get()))) {
            throw new SkillVersionNotIncreasingException(
                    "新版本号(" + newVersion + ")必须严格大于当前最大版本号(" + currentMax.get() + ")");
        }

        RepackagedArtifact artifact = validator.repackage(parsed, skill.name());
        String cardExcerpt = computeCardExcerpt(parsed, artifact);
        String artifactKey = buildArtifactKey(skill.name(), newVersion, artifact.sha256());
        artifactStorage.store(artifactKey, artifact.zipBytes());

        long versionId = skillRepository.insertVersion(newVersionDraft(
                skillId, newVersion, artifactKey, parsed, artifact, command.actingUser().userId()));
        skillRepository.updateLatestVersionIfGreater(
                skillId, versionId, newVersion.raw(), parsed.description(), cardExcerpt);

        auditLog.info(
                "skill 新版本发布完成, event={}, skillId={}, skillName={}, skillVersion={}, operator={}",
                "skill_version_published",
                skillId,
                skill.name().value(),
                newVersion.raw(),
                command.actingUser().userId());

        return requireSkill(skillId);
    }

    private void requireOwnerOrAdmin(Skill skill, AuthenticatedUser actingUser) {
        boolean allowed = actingUser.isAdmin() || actingUser.userId() == skill.owner().userId();
        if (!allowed) {
            throw new AccessDeniedException("只有 owner 或 ADMIN 能给这个 skill 发布新版本");
        }
    }

    private SkillVersion newVersionDraft(
            long skillId,
            SemVer version,
            String artifactKey,
            ParsedSkillPackage parsed,
            RepackagedArtifact artifact,
            long actingUserId) {
        // certification 恒为 NONE:新版本不继承认证(业务建模文档 5.8),certifiedBy/At/Note 全部留空。
        return new SkillVersion(
                null,
                skillId,
                version.raw(),
                artifactKey,
                parsed.contentDigest(),
                artifact.sha256(),
                (long) artifact.zipBytes().length,
                parsed.fileCount(),
                parsed.allowedTools(),
                artifact.rewrittenSkillMdContent(),
                parsed.readmeContent(),
                null,
                Certification.NONE,
                null,
                null,
                null,
                actingUserId,
                null);
    }

    /** README 优先,没有则退回改写后的 SKILL.md 全文(业务建模文档 5.6)。 */
    private String computeCardExcerpt(ParsedSkillPackage parsed, RepackagedArtifact artifact) {
        String source = parsed.hasReadme() ? parsed.readmeContent() : artifact.rewrittenSkillMdContent();
        return CardExcerptGenerator.generate(source);
    }

    private SkillName resolveFinalNameForNewSkill(ParsedSkillPackage parsed) {
        SkillName base = new SkillName(parsed.name());
        if (skillRepository.findByName(base.value()).isEmpty()) {
            return base;
        }
        String suggested = suggestName(parsed.name(), parsed.contentDigest());
        if (suggested == null) {
            throw new ResourceConflictException(
                    "SKILL_NAME_DUPLICATED", "已存在同名 skill,且短哈希升位仍冲突: " + parsed.name());
        }
        return new SkillName(suggested);
    }

    /** 6/8/10/12 位短哈希逐级升位,全部冲突则返回 {@code null}(业务建模文档 5.5)。 */
    private String suggestName(String baseName, String contentDigest) {
        for (int prefixLen : SUGGESTION_PREFIX_LENGTHS) {
            String candidate = baseName + "-" + contentDigest.substring(0, prefixLen);
            if (skillRepository.findByName(candidate).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private String buildArtifactKey(SkillName name, SemVer version, String sha256) {
        return name.value() + "/" + version.raw() + "/" + sha256 + ".zip";
    }

    private Skill requireSkill(long skillId) {
        return skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
    }

    private ResourceNotFoundException notFound(long skillId) {
        return new ResourceNotFoundException(SKILL_NOT_FOUND_CODE, "skill 不存在, id=" + skillId);
    }
}
