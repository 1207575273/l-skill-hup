package com.landray.skillhub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnauthenticatedExceptionTest {

    @Test
    void should_mapToHttp401_when_constructed() {
        UnauthenticatedException ex = new UnauthenticatedException("token 已过期,请重新登录");

        assertThat(ex.httpStatus()).isEqualTo(401);
        assertThat(ex.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(ex.getMessage()).isEqualTo("token 已过期,请重新登录");
    }
}
