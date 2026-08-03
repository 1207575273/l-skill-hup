package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillPackageInvalidExceptionTest {

    @Test
    void should_mapToHttp400WithGivenCodeAndMessage_when_constructed() {
        SkillPackageInvalidException ex = new SkillPackageInvalidException("SKILL_PACKAGE_ZIP_BOMB", "压缩比超限");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("SKILL_PACKAGE_ZIP_BOMB");
        assertThat(ex.getMessage()).isEqualTo("压缩比超限");
    }
}
