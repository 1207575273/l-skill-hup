package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnonymousFreeTagNotAllowedExceptionTest {

    @Test
    void should_mapToHttp400WithFixedMessage_when_constructed() {
        AnonymousFreeTagNotAllowedException ex = new AnonymousFreeTagNotAllowedException();

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("ANONYMOUS_FREE_TAG_NOT_ALLOWED");
        assertThat(ex.getMessage()).contains("匿名上传不能新建自由标签");
    }
}
