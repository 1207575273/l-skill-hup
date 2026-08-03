package com.landray.skillhub.application.install;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.domain.exception.ClientScopeNotSupportedException;
import com.landray.skillhub.domain.exception.InstallBundleExpiredException;
import com.landray.skillhub.domain.model.ClientTarget;
import com.landray.skillhub.domain.model.InstallBundle;
import com.landray.skillhub.domain.model.InstallItem;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.repository.InstallBundleRepository;
import com.landray.skillhub.domain.repository.SkillArtifactRepository;
import com.landray.skillhub.domain.service.InstallScriptRenderer;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.SensitiveValueMasker;

/**
 * 安装链路用例编排:客户端适配表、生成安装单、渲染安装脚本(业务建模文档 6.2"安装"一组)。
 *
 * <p>{@code @Transactional} 只出现在这一层(backend/CLAUDE.md 第一节)。
 *
 * <p><b>过期清理为什么固定 30 天</b>:业务建模文档 4.13 明写"每次 {@code POST /bundles} 顺手删除 30 天前过期的行"
 * ——这是文档定死的具体数值,不是本轮实现自选的,与 {@link InstallProperties#bundleTtlDays()}(安装单本身的有效期,
 * 文档未给数值、本轮按 30 天实现)是两个独立的常量,恰好同为 30 只是巧合,不要以为改一个另一个也要跟着改。
 */
@Service
public class InstallApplicationService {

    private static final Logger log = LoggerFactory.getLogger(InstallApplicationService.class);

    /**
     * 固定 logger name,专供审计事件使用(backend/CLAUDE.md 6.6)。
     *
     * <p><b>刻意不做成 {@code private static final Logger} 字段</b>——ArchUnit 的
     * {@code loggerFieldsMustFollowConvention} 规则要求任何 {@code Logger} 类型字段都必须命名为
     * {@code log}(backend/CLAUDE.md 6.1),而这个类已经有一个指向自身类名的 {@code log} 字段,一个类不能有两个
     * 同名字段。改成"按需现取"的私有方法:{@link org.slf4j.LoggerFactory#getLogger(String)} 内部按名字缓存
     * {@code Logger} 实例,重复调用不产生新对象,开销可忽略。
     */
    private static Logger auditLog() {
        return LoggerFactory.getLogger("skillhub.audit");
    }

    /** 业务建模文档 4.13:清理"过期超过 30 天"的安装单行,这个数值文档已定死,不可配置。 */
    private static final Duration EXPIRED_CLEANUP_THRESHOLD = Duration.ofDays(30);

    private static final String INSTALL_BUNDLE_NOT_FOUND_CODE = "INSTALL_BUNDLE_NOT_FOUND";
    private static final String SKILL_VERSION_NOT_FOUND_CODE = "SKILL_VERSION_NOT_FOUND";

    private final InstallBundleRepository installBundleRepository;
    private final SkillArtifactRepository skillArtifactRepository;
    private final InstallProperties installProperties;

    public InstallApplicationService(
            InstallBundleRepository installBundleRepository,
            SkillArtifactRepository skillArtifactRepository,
            InstallProperties installProperties) {
        this.installBundleRepository = installBundleRepository;
        this.skillArtifactRepository = skillArtifactRepository;
        this.installProperties = installProperties;
    }

    /** {@code GET /clients}:只返回已实测的 {@code (client, scope)} 组合(业务建模文档 5.9)。 */
    public List<ClientTarget> listSupportedClients() {
        return ClientTarget.testedTargets();
    }

    /**
     * {@code POST /bundles}:校验 {@code (client, scope)} 已实测、校验每个条目确实指向存在的 skill 版本,
     * 生成安装单并顺手清理陈旧的过期行。
     */
    @Transactional
    public CreateBundleResult createBundle(
            List<InstallItem> items, String clientParam, String scopeParam, Long createdByUserId) {
        ClientTarget target = ClientTarget.find(clientParam, scopeParam)
                .orElseThrow(() -> new ClientScopeNotSupportedException(clientParam, scopeParam));

        List<Long> versionIds = items.stream().map(InstallItem::versionId).distinct().toList();
        List<SkillArtifact> resolved = skillArtifactRepository.findByVersionIds(versionIds);
        validateItemsResolvable(items, resolved);

        Instant now = Instant.now();
        InstallBundle bundle = InstallBundle.create(
                items, target.client(), target.scope(), createdByUserId, now,
                Duration.ofDays(installProperties.bundleTtlDays()));
        InstallBundle saved = installBundleRepository.save(bundle);

        int deletedCount = installBundleRepository.deleteExpiredBefore(now.minus(EXPIRED_CLEANUP_THRESHOLD));
        if (deletedCount > 0) {
            log.debug("已清理过期超过 30 天的安装单, deletedCount={}", deletedCount);
        }

        auditLog().info(
                "安装单已生成, event=install_bundle_created, token={}, client={}, scope={}, itemCount={}, "
                        + "createdByUserId={}",
                SensitiveValueMasker.mask(saved.token()),
                target.client().apiCode(),
                target.scope().apiCode(),
                items.size(),
                createdByUserId);

        return new CreateBundleResult(
                saved.token(), saved.expiresAt(), target.client().apiCode(), target.scope().apiCode(),
                installProperties.publicBaseUrl());
    }

    /** {@code GET /i/{token}.ps1}。 */
    @Transactional(readOnly = true)
    public RenderedScript renderPowerShell(String token, String clientIp) {
        return render(token, clientIp, InstallScriptRenderer::renderPowerShell);
    }

    /** {@code GET /i/{token}.sh}。 */
    @Transactional(readOnly = true)
    public RenderedScript renderBash(String token, String clientIp) {
        return render(token, clientIp, InstallScriptRenderer::renderBash);
    }

    private RenderedScript render(String token, String clientIp, ScriptRenderFunction renderFunction) {
        InstallBundle bundle = installBundleRepository
                .findByToken(token)
                .orElseThrow(() -> {
                    log.warn("安装单不存在或 token 无效, token={}", SensitiveValueMasker.mask(token));
                    return new ResourceNotFoundException(INSTALL_BUNDLE_NOT_FOUND_CODE, "安装单不存在或 token 无效");
                });

        Instant now = Instant.now();
        if (bundle.isExpired(now)) {
            log.warn("安装单已过期, token={}, expiresAt={}", SensitiveValueMasker.mask(token), bundle.expiresAt());
            throw new InstallBundleExpiredException();
        }

        List<Long> versionIds =
                bundle.items().stream().map(InstallItem::versionId).toList();
        List<SkillArtifact> resolved = skillArtifactRepository.findByVersionIds(versionIds);
        List<SkillArtifact> ordered = orderByItems(bundle.items(), resolved);

        auditRevokedHits(token, clientIp, ordered);

        String content = renderFunction.render(bundle, ordered, installProperties.publicBaseUrl());
        return new RenderedScript(content);
    }

    /** 命中 {@code REVOKED} 时打 {@code skillhub.audit} 的 WARN 事件(业务建模文档 5.9)——这是安全事件,必须留痕。 */
    private void auditRevokedHits(String token, String clientIp, List<SkillArtifact> ordered) {
        for (SkillArtifact artifact : ordered) {
            if (!artifact.isRevoked()) {
                continue;
            }
            auditLog().warn(
                    "安装单命中已撤销认证的版本, event=install_revoked_hit, token={}, skillName={}, skillVersion={}, "
                            + "clientIp={}",
                    SensitiveValueMasker.mask(token),
                    artifact.skillName(),
                    artifact.version(),
                    clientIp);
        }
    }

    /** 按 {@code bundle.items()} 的原始顺序排列——保持用户当初勾选的顺序,而不是数据库返回的任意顺序。 */
    private List<SkillArtifact> orderByItems(List<InstallItem> items, List<SkillArtifact> resolved) {
        Map<Long, SkillArtifact> byVersionId =
                resolved.stream().collect(Collectors.toMap(SkillArtifact::skillVersionId, Function.identity()));
        return items.stream()
                .map(item -> byVersionId.get(item.versionId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 校验每个 {@code (skillId, versionId)} 都确实指向存在的版本,且 skillId 与该版本实际所属一致。 */
    private void validateItemsResolvable(List<InstallItem> items, List<SkillArtifact> resolved) {
        Map<Long, SkillArtifact> byVersionId =
                resolved.stream().collect(Collectors.toMap(SkillArtifact::skillVersionId, Function.identity()));
        for (InstallItem item : items) {
            SkillArtifact artifact = byVersionId.get(item.versionId());
            if (artifact == null || !artifact.skillId().equals(item.skillId())) {
                throw new ResourceNotFoundException(
                        SKILL_VERSION_NOT_FOUND_CODE,
                        "skill 版本不存在或与 skillId 不匹配, skillId=" + item.skillId() + ", versionId="
                                + item.versionId());
            }
        }
    }

    @FunctionalInterface
    private interface ScriptRenderFunction {
        String render(InstallBundle bundle, List<SkillArtifact> artifacts, String publicBaseUrl);
    }
}
