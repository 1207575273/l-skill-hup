package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserDisabledExceptionTest {

    @Test
    void should_mapToHttp403_when_constructed() {
        UserDisabledException ex = new UserDisabledException();

        assertThat(ex.httpStatus()).isEqualTo(403);
        assertThat(ex.code()).isEqualTo("USER_DISABLED");
    }
}
