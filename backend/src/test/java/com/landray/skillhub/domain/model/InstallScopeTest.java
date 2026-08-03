package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstallScopeTest {

    @Test
    void should_resolveCaseInsensitively_when_apiCodeMatchesKnownValue() {
        assertThat(InstallScope.fromApiCode("global")).contains(InstallScope.GLOBAL);
        assertThat(InstallScope.fromApiCode("PROJECT")).contains(InstallScope.PROJECT);
    }

    @Test
    void should_returnEmpty_when_apiCodeIsUnknownOrBlank() {
        assertThat(InstallScope.fromApiCode("workspace")).isEmpty();
        assertThat(InstallScope.fromApiCode(null)).isEmpty();
    }
}
