package com.landray.skillhub.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.CertificationDecision;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

/**
 * 认证状态变更(业务建模文档 5.8)。ADMIN 专属,鉴权由 {@code AuthenticationInterceptor} 按
 * {@code @RequireAdmin} 统一把关。
 *
 * <p><b>为什么单独一个服务而不是塞进 {@code SkillApplicationService}</b>:那个类承载的是 owner / ADMIN
 * 都能做的日常动作(改元数据、下架、删除),而认证是纯粹的平台治理动作,权限档次与判断逻辑都不一样。
 * 合在一起会让"这个方法要不要判 owner"变成一句"看情况",而权限漏判最爱藏在这种地方。
 *
 * <p><b>这一层刻意什么都不做,只做三件事</b>:定位(skill 与版本都必须存在且对得上)、落库、留痕。
 * 认证入参的全部规则在 {@link CertificationDecision} 里,别在这里再判一遍——判两遍迟早只改一遍。
 */
@Service
public class SkillCertificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillCertificationApplicationService.class);

    private final SkillRepository skillRepository;
    private final AuditLog auditLog;

    public SkillCertificationApplicationService(SkillRepository skillRepository, AuditLog auditLog) {
        this.skillRepository = skillRepository;
        this.auditLog = auditLog;
    }

    /**
     * 认证或撤销某个版本。
     *
     * <p><b>撤销不下架 skill</b>(5.8):撤销是"这个版本有问题,自己判断",下架是"这个 skill 不该再被用",
     * 两者烈度不同。本方法只写 {@code skill_version} 的认证列,一个字都不碰 {@code skill.status}。
     *
     * <p>平台能做的到此为止。已经装到别人机器上的 skill 召不回来——撤销能起作用的地方是<b>后续</b>安装:
     * 安装脚本渲染时实时读认证状态,命中 {@code REVOKED} 就默认中止(5.9)。所以这条路径与那条渲染路径是
     * 同一个机制的两半,任何一半失效整套就失效。
     *
     * @return 变更后的版本
     */
    @Transactional
    public SkillVersion certify(
            long skillId, String versionString, CertificationDecision decision, AuthenticatedUser admin) {
        Skill skill = skillRepository
                .findDetailById(skillId)
                .orElseThrow(() -> new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在, skillId=" + skillId));
        SkillVersion version = skillRepository
                .findVersionBySkillIdAndVersion(skillId, versionString)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SKILL_VERSION_NOT_FOUND", "版本不存在, skillId=" + skillId + ", version=" + versionString));

        skillRepository.updateCertification(
                version.id(), decision.certification(), admin.userId(), decision.note());

        // 安全事件一律 WARN 起步(backend/CLAUDE.md 6.6)。认证状态直接决定别人装不装得下去,
        // 值得一条能被巡检看到的记录,而不是淹没在 INFO 里。
        log.warn(
                "认证状态已变更, skillId={}, skillName={}, skillVersion={}, certification={}, operator={}",
                skillId,
                skill.name().value(),
                versionString,
                decision.certification(),
                admin.userId());
        auditLog.info(
                "认证状态变更, event={}, skillId={}, skillName={}, skillVersion={}, certification={}, note={},"
                        + " operator={}",
                "skill_version_certified",
                skillId,
                skill.name().value(),
                versionString,
                decision.certification(),
                decision.note(),
                admin.userId());

        return skillRepository
                .findVersionBySkillIdAndVersion(skillId, versionString)
                .orElseThrow(() -> new IllegalStateException(
                        "刚更新过的版本重查不到, skillId=" + skillId + ", version=" + versionString));
    }
}
