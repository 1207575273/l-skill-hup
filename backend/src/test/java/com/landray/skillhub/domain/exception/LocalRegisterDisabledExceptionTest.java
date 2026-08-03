package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalRegisterDisabledExceptionTest {

    @Test
    void should_mapToHttp403NotFound_when_constructed() {
        LocalRegisterDisabledException ex = new LocalRegisterDisabledException();

        assertThat(ex.httpStatus()).isEqualTo(403);
        assertThat(ex.code()).isEqualTo("LOCAL_REGISTER_DISABLED");
    }
}
