package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillArtifactTest {

    private SkillArtifact artifact(SkillStatus status, Certification certification) {
        return new SkillArtifact(
                10L, 1L, "code-review", "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), 1024L, status,
                certification, null);
    }

    @Test
    void should_reportRevoked_when_certificationIsRevoked() {
        assertThat(artifact(SkillStatus.PUBLISHED, Certification.REVOKED).isRevoked()).isTrue();
        assertThat(artifact(SkillStatus.PUBLISHED, Certification.CERTIFIED).isRevoked()).isFalse();
        assertThat(artifact(SkillStatus.PUBLISHED, Certification.NONE).isRevoked()).isFalse();
    }

    @Test
    void should_reportUnlisted_when_skillStatusIsUnlisted() {
        assertThat(artifact(SkillStatus.UNLISTED, Certification.NONE).isUnlisted()).isTrue();
        assertThat(artifact(SkillStatus.PUBLISHED, Certification.NONE).isUnlisted()).isFalse();
    }
}
