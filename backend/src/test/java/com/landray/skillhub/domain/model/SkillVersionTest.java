package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillVersionTest {

    private SkillVersion versionWith(Certification certification) {
        return new SkillVersion(
                1L, 10L, "1.0.0", 1024L, 3, List.of("Read", "Grep"), "# skill", null, null, certification, null, null,
                null, 1L, null);
    }

    @Test
    @DisplayName("certification 为 CERTIFIED 时 isCertified 为 true")
    void should_beCertified_when_certificationIsCertified() {
        assertThat(versionWith(Certification.CERTIFIED).isCertified()).isTrue();
        assertThat(versionWith(Certification.CERTIFIED).isRevoked()).isFalse();
    }

    @Test
    @DisplayName("certification 为 REVOKED 时 isRevoked 为 true")
    void should_beRevoked_when_certificationIsRevoked() {
        assertThat(versionWith(Certification.REVOKED).isRevoked()).isTrue();
        assertThat(versionWith(Certification.REVOKED).isCertified()).isFalse();
    }

    @Test
    @DisplayName("certification 为 NONE 时两者皆为 false")
    void should_beNeither_when_certificationIsNone() {
        SkillVersion version = versionWith(Certification.NONE);

        assertThat(version.isCertified()).isFalse();
        assertThat(version.isRevoked()).isFalse();
    }
}
