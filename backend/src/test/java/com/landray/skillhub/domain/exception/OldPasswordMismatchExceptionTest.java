package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OldPasswordMismatchExceptionTest {

    @Test
    void should_mapToDefaultHttp400_when_constructed() {
        OldPasswordMismatchException ex = new OldPasswordMismatchException();

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("OLD_PASSWORD_INVALID");
    }
}
