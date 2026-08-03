package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillVersionNameMismatchExceptionTest {

    @Test
    void should_mapToHttp400WithGivenMessage_when_constructed() {
        SkillVersionNameMismatchException ex = new SkillVersionNameMismatchException("包内 name 与目标 skill 不一致");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("SKILL_VERSION_NAME_MISMATCH");
        assertThat(ex.getMessage()).isEqualTo("包内 name 与目标 skill 不一致");
    }
}
