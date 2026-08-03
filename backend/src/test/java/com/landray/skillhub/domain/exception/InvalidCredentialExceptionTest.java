package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidCredentialExceptionTest {

    @Test
    void should_mapToHttp401WithFixedGenericMessage_when_constructed() {
        InvalidCredentialException ex = new InvalidCredentialException();

        assertThat(ex.httpStatus()).isEqualTo(401);
        assertThat(ex.code()).isEqualTo("LOGIN_FAILED");
        assertThat(ex.getMessage()).isEqualTo("登录名或密码错误");
    }
}
