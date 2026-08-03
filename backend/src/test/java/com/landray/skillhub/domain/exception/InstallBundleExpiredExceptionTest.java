package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstallBundleExpiredExceptionTest {

    @Test
    void should_mapToHttp410Gone_when_constructed() {
        InstallBundleExpiredException ex = new InstallBundleExpiredException();

        assertThat(ex.httpStatus()).isEqualTo(410);
        assertThat(ex.code()).isEqualTo("INSTALL_BUNDLE_EXPIRED");
    }
}
