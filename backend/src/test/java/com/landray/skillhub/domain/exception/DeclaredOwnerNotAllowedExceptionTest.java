package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeclaredOwnerNotAllowedExceptionTest {

    @Test
    void should_mapToHttp400WithFixedMessage_when_constructed() {
        DeclaredOwnerNotAllowedException ex = new DeclaredOwnerNotAllowedException();

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("DECLARED_OWNER_NOT_ALLOWED");
        assertThat(ex.getMessage()).contains("归属人就是你本人");
    }
}
