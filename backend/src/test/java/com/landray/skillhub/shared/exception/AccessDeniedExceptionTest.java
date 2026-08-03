package com.landray.skillhub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessDeniedExceptionTest {

    @Test
    void should_mapToHttp403_when_constructed() {
        AccessDeniedException ex = new AccessDeniedException("需要 ADMIN 权限");

        assertThat(ex.httpStatus()).isEqualTo(403);
        assertThat(ex.code()).isEqualTo("ACCESS_DENIED");
        assertThat(ex.getMessage()).isEqualTo("需要 ADMIN 权限");
    }
}
