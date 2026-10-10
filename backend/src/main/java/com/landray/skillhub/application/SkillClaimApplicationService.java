package com.landray.skillhub.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.ResourceConflictException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

/**
 * 认领:把一个无主 skill 的归属落到当前登录人身上。
 *
 * <p>无主来自两条路:匿名上传(上传不再要求登录),以及早先导入的包。两者都挂在内置 system 账号名下
 * (见 {@link com.landray.skillhub.domain.model.Skill#isUnclaimed()}),都应当可被认领接手,所以不区分。
 *
 * <p><b>为什么单独一个服务</b>:与 {@link SkillCertificationApplicationService} 同样的理由 ——
 * {@code SkillApplicationService} 承载的是 owner / ADMIN 都能做的日常动作,而认领恰恰发生在"还没有 owner"
 * 这个前置条件下,权限判断的形状和那些方法完全不同。合在一起会让"这个方法要不要判 owner"变成一句"看情况"。
 *
 * <p><b>认领是先到先得,且这件事的仲裁在 SQL 里。</b>刻意不做工号校验之类的资格门槛:声明归属里的工号是
 * 上传者自称的、未经核实,而本地建号普遍没绑工号(线上三个账号 employee_no 全空),拿它当门槛的实际效果是
 * 声明了工号的包谁都认领不了。换来的代价是"可以认领别人匿名上传的包",用审计留痕 + ADMIN 可改派来兜。
 */
@Service
public class SkillClaimApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillClaimApplicationService.class);

    private static final String ALREADY_CLAIMED_CODE = "SKILL_ALREADY_CLAIMED";

    private final SkillRepository skillRepository;
    private final AuditLog auditLog;

    public SkillClaimApplicationService(SkillRepository skillRepository, AuditLog auditLog) {
        this.skillRepository = skillRepository;
        this.auditLog = auditLog;
    }

    /**
     * 认领一个无主 skill。
     *
     * <p><b>这里有两道"已被认领"的判断,不是重复。</b>前一道读出来判,为的是在常见情形下给出一句说得清的
     * 错误(谁已经认领了它);后一道是仓储的比较并交换,为的是并发正确性 —— 两个人同时点认领时,前一道会
     * 同时放行两个请求,只有后一道能让其中一个失败。少了前者体验差,少了后者会静默覆盖别人的归属。
     *
     * @return 认领后的 skill(重新读一遍,带上新的归属信息)
     */
    @Transactional
    public Skill claim(long skillId, AuthenticatedUser actingUser) {
        Skill skill = requireSkill(skillId);
        if (!skill.isUnclaimed()) {
            throw new ResourceConflictException(
                    ALREADY_CLAIMED_CODE,
                    "这个 skill 已经有归属人了: " + skill.owner().displayName());
        }

        if (!skillRepository.claimOwnership(skillId, actingUser.userId())) {
            // 走到这里说明在上面那次读取与这次写入之间,有人抢先一步认领了。
            throw new ResourceConflictException(ALREADY_CLAIMED_CODE, "这个 skill 刚刚被其他人认领了,请刷新后查看");
        }

        // 归属变更改变的是"谁能给这个包发新版本 / 改它 / 删它",是一次权限面的变化,值得留痕。
        // 用 WARN 而不是 INFO:backend/CLAUDE.md 6.6 要求安全相关事件 WARN 起步。
        log.warn(
                "skill 归属已认领, skillId={}, skillName={}, declaredOwner={}, newOwnerUserId={}",
                skillId,
                skill.name().value(),
                skill.declaredOwner() == null ? "<未声明>" : skill.declaredOwner().name(),
                actingUser.userId());
        auditLog.info(
                "skill 认领完成, event={}, skillId={}, skillName={}, declaredOwnerName={}, operator={}",
                "skill_claimed",
                skillId,
                skill.name().value(),
                skill.declaredOwner() == null ? null : skill.declaredOwner().name(),
                actingUser.userId());

        return requireSkill(skillId);
    }

    private Skill requireSkill(long skillId) {
        return skillRepository
                .findDetailById(skillId)
                .orElseThrow(() -> new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在, skillId=" + skillId));
    }
}
