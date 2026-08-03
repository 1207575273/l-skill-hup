package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientScopeNotSupportedExceptionTest {

    @Test
    void should_mapToHttp400AndCarryClientScopeDetails_when_constructed() {
        ClientScopeNotSupportedException ex = new ClientScopeNotSupportedException("workbuddy", "project");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("CLIENT_SCOPE_NOT_SUPPORTED");
        assertThat(ex.details()).containsEntry("client", "workbuddy").containsEntry("scope", "project");
    }
}
