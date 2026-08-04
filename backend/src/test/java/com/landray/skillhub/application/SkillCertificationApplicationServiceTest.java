package com.landray.skillhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.CertificationDecision;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillOwner;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;

/**
 * 认证状态变更的编排。mock 掉仓储,只验编排本身(backend/CLAUDE.md 8.1)。
 *
 * <p>入参规则(撤销必填理由、长度上限、状态解析)全在 {@code CertificationDecisionTest} 里,这里不重复验——
 * 那是值对象的责任,在这一层再验一遍就是同一条规则有两个事实源。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillCertificationApplicationServiceTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(9L, UserRole.ADMIN, 1L, 1L);
    private static final long SKILL_ID = 42L;
    private static final long VERSION_ID = 100L;
    private static final String VERSION = "1.2.0";

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private AuditLog auditLog;

    private SkillCertificationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SkillCertificationApplicationService(skillRepository, auditLog);
    }

    private static Skill skill() {
        return new Skill(
                SKILL_ID,
                new SkillName("code-review"),
                "代码审查",
                "描述",
                SkillSource.INTERNAL,
                new SkillOwner(7L, "张三", null),
                "摘要",
                SkillStatus.PUBLISHED,
                0L,
                0,
                0,
                0,
                0,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                List.of());
    }

    private static SkillVersion version(Certification certification, String note) {
        return new SkillVersion(
                VERSION_ID, SKILL_ID, VERSION, null, null, null, 1024, 3, List.of(), null, null, null,
                certification, null, null, note, 7L, Instant.EPOCH);
    }

    private void givenExistingVersion(Certification certification) {
        when(skillRepository.findDetailById(SKILL_ID)).thenReturn(Optional.of(skill()));
        when(skillRepository.findVersionBySkillIdAndVersion(SKILL_ID, VERSION))
                .thenReturn(Optional.of(version(certification, null)));
    }

    @Test
    @DisplayName("认证时把状态、操作者、意见一起写进对应的 versionId")
    void should_writeCertification_when_adminCertifies() {
        givenExistingVersion(Certification.NONE);

        service.certify(SKILL_ID, VERSION, new CertificationDecision(Certification.CERTIFIED, "看过了"), ADMIN);

        verify(skillRepository).updateCertification(VERSION_ID, Certification.CERTIFIED, 9L, "看过了");
    }

    @Test
    @DisplayName("撤销认证时理由被原样落库——它要进安装脚本的警示文本")
    void should_persistNote_when_revoking() {
        givenExistingVersion(Certification.CERTIFIED);

        service.certify(
                SKILL_ID, VERSION, new CertificationDecision(Certification.REVOKED, "存在命令注入风险"), ADMIN);

        verify(skillRepository)
                .updateCertification(VERSION_ID, Certification.REVOKED, 9L, "存在命令注入风险");
    }

    /**
     * 5.8 写死"撤销认证不自动下架 skill":撤销是"这个版本有问题,自己判断",下架是"这个 skill 不该再被用",
     * 两者烈度不同。顺手下架会让作者莫名其妙地发现自己的 skill 从列表里消失了。
     */
    @Test
    @DisplayName("撤销认证不改 skill 状态")
    void should_notTouchSkillStatus_when_revoking() {
        givenExistingVersion(Certification.CERTIFIED);

        service.certify(SKILL_ID, VERSION, new CertificationDecision(Certification.REVOKED, "有问题"), ADMIN);

        verify(skillRepository, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("允许改回 NONE——那是撤销一次误判的唯一出口")
    void should_allowResetToNone() {
        givenExistingVersion(Certification.CERTIFIED);

        service.certify(SKILL_ID, VERSION, new CertificationDecision(Certification.NONE, null), ADMIN);

        verify(skillRepository).updateCertification(VERSION_ID, Certification.NONE, 9L, null);
    }

    @Test
    @DisplayName("skill 不存在时 404,不去查版本")
    void should_reject_when_skillMissing() {
        when(skillRepository.findDetailById(SKILL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.certify(
                        SKILL_ID, VERSION, new CertificationDecision(Certification.CERTIFIED, null), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(skillRepository, never()).findVersionBySkillIdAndVersion(anyLong(), anyString());
        verify(skillRepository, never()).updateCertification(anyLong(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("版本号不存在时 404,不落库")
    void should_reject_when_versionMissing() {
        when(skillRepository.findDetailById(SKILL_ID)).thenReturn(Optional.of(skill()));
        when(skillRepository.findVersionBySkillIdAndVersion(SKILL_ID, "9.9.9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.certify(
                        SKILL_ID, "9.9.9", new CertificationDecision(Certification.CERTIFIED, null), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9.9.9");

        verify(skillRepository, never()).updateCertification(anyLong(), any(), anyLong(), any());
    }

    /** 事后追查时 skillId 可能已经查不到主体,只有当时记下的名字还能对上人(5.8 明确列了这几个字段)。 */
    @Test
    @DisplayName("审计事件带上 skillName 与 skillVersion")
    void should_writeAuditEvent_when_certificationChanges() {
        givenExistingVersion(Certification.NONE);

        service.certify(SKILL_ID, VERSION, new CertificationDecision(Certification.CERTIFIED, "ok"), ADMIN);

        verify(auditLog)
                .info(
                        anyString(),
                        eq("skill_version_certified"),
                        eq(SKILL_ID),
                        eq("code-review"),
                        eq(VERSION),
                        eq(Certification.CERTIFIED),
                        eq("ok"),
                        eq(9L));
    }

    @Test
    @DisplayName("返回的是变更后的版本,不是变更前那份")
    void should_returnReloadedVersion_when_certified() {
        when(skillRepository.findDetailById(SKILL_ID)).thenReturn(Optional.of(skill()));
        when(skillRepository.findVersionBySkillIdAndVersion(SKILL_ID, VERSION))
                .thenReturn(Optional.of(version(Certification.NONE, null)))
                .thenReturn(Optional.of(version(Certification.CERTIFIED, "看过了")));

        SkillVersion result =
                service.certify(SKILL_ID, VERSION, new CertificationDecision(Certification.CERTIFIED, "看过了"), ADMIN);

        assertThat(result.certification()).isEqualTo(Certification.CERTIFIED);
        assertThat(result.certifyNote()).isEqualTo("看过了");
    }
}
