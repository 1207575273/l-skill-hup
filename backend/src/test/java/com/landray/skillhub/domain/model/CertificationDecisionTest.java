package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.CertificationDecisionInvalidException;

/** 认证状态变更的入参规则(业务建模文档 5.8)。纯 Java,不起 Spring。 */
class CertificationDecisionTest {

    /**
     * 这条是本类存在的理由:撤销理由会原样进安装脚本的警示文本(5.9),是使用者判断"要不要继续装"的唯一依据。
     * 允许空理由等于给出一个只会吓人、不提供信息的红色警告,而那种警告的下场是被无视。
     */
    @Test
    void should_rejectRevoke_when_noteMissing() {
        assertThatThrownBy(() -> new CertificationDecision(Certification.REVOKED, null))
                .isInstanceOf(CertificationDecisionInvalidException.class)
                .hasMessageContaining("撤销");
    }

    @Test
    void should_rejectRevoke_when_noteIsBlank() {
        assertThatThrownBy(() -> new CertificationDecision(Certification.REVOKED, "   "))
                .isInstanceOf(CertificationDecisionInvalidException.class);
    }

    @Test
    void should_acceptRevoke_when_noteGiven() {
        CertificationDecision decision = new CertificationDecision(Certification.REVOKED, "存在命令注入风险");

        assertThat(decision.note()).isEqualTo("存在命令注入风险");
    }

    /** CERTIFIED 与 NONE 的意见是可选的:通过认证时没什么可补充是常态。 */
    @Test
    void should_allowMissingNote_when_certifying() {
        assertThat(new CertificationDecision(Certification.CERTIFIED, null).note()).isNull();
        assertThat(new CertificationDecision(Certification.NONE, null).note()).isNull();
    }

    @Test
    void should_trimNote_when_padded() {
        assertThat(new CertificationDecision(Certification.CERTIFIED, "  看过了  ").note()).isEqualTo("看过了");
    }

    /** 库里同时出现 null 和 "" 两种"没有意见",下游每次都要判两个条件,漏一个就是页面上多一行空白。 */
    @Test
    void should_normalizeBlankNoteToNull() {
        assertThat(new CertificationDecision(Certification.CERTIFIED, "   ").note()).isNull();
    }

    @Test
    void should_rejectNote_when_longerThan256Chars() {
        assertThatThrownBy(() -> new CertificationDecision(Certification.CERTIFIED, "字".repeat(257)))
                .isInstanceOf(CertificationDecisionInvalidException.class)
                .hasMessageContaining("256");
    }

    @Test
    void should_acceptNote_when_exactly256Chars() {
        assertThat(new CertificationDecision(Certification.CERTIFIED, "字".repeat(256)).note()).hasSize(256);
    }

    @Test
    void should_rejectNullCertification() {
        assertThatThrownBy(() -> new CertificationDecision(null, "任意"))
                .isInstanceOf(CertificationDecisionInvalidException.class);
    }

    @Test
    void should_parseCertification_caseInsensitively() {
        assertThat(CertificationDecision.of("certified", null).certification())
                .isEqualTo(Certification.CERTIFIED);
        assertThat(CertificationDecision.of("  Revoked ", "理由").certification())
                .isEqualTo(Certification.REVOKED);
    }

    @Test
    void should_rejectUnknownCertification() {
        assertThatThrownBy(() -> CertificationDecision.of("APPROVED", null))
                .isInstanceOf(CertificationDecisionInvalidException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void should_rejectBlankCertification() {
        assertThatThrownBy(() -> CertificationDecision.of("  ", null))
                .isInstanceOf(CertificationDecisionInvalidException.class);
    }

    /** 走 of() 与直接 new 必须落到同一条不变量上,否则接口层那条路会绕开校验。 */
    @Test
    void should_applyRevokeNoteRule_when_constructedFromParams() {
        assertThatThrownBy(() -> CertificationDecision.of("REVOKED", "  "))
                .isInstanceOf(CertificationDecisionInvalidException.class);
    }
}
