package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidSkillVersionExceptionTest {

    @Test
    void should_mapToHttp400WithGivenMessage_when_constructed() {
        InvalidSkillVersionException ex = new InvalidSkillVersionException("版本号格式不合法");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("SKILL_VERSION_INVALID");
        assertThat(ex.getMessage()).isEqualTo("版本号格式不合法");
    }
}
