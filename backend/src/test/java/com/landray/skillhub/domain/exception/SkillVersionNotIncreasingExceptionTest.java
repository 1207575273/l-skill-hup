package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillVersionNotIncreasingExceptionTest {

    @Test
    void should_mapToHttp400WithGivenMessage_when_constructed() {
        SkillVersionNotIncreasingException ex = new SkillVersionNotIncreasingException("版本号必须严格递增");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("SKILL_VERSION_NOT_INCREASING");
        assertThat(ex.getMessage()).isEqualTo("版本号必须严格递增");
    }
}
