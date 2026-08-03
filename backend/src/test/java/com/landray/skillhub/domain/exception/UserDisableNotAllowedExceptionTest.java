package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserDisableNotAllowedExceptionTest {

    @Test
    void should_mapToDefaultHttp400WithGivenMessage_when_constructed() {
        UserDisableNotAllowedException ex = new UserDisableNotAllowedException("不能禁用自己");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("USER_DISABLE_NOT_ALLOWED");
        assertThat(ex.getMessage()).isEqualTo("不能禁用自己");
    }
}
