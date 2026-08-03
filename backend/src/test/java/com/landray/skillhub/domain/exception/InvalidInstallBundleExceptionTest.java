package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidInstallBundleExceptionTest {

    @Test
    void should_mapToHttp400WithGivenMessage_when_constructed() {
        InvalidInstallBundleException ex = new InvalidInstallBundleException("安装单至少需要包含一个 skill");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("INSTALL_BUNDLE_ITEMS_INVALID");
        assertThat(ex.getMessage()).isEqualTo("安装单至少需要包含一个 skill");
    }
}
