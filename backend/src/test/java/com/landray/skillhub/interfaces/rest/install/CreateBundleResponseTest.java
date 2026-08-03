package com.landray.skillhub.interfaces.rest.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.landray.skillhub.application.install.CreateBundleResult;

class CreateBundleResponseTest {

    @Test
    void should_buildCopyPasteCommands_when_publicBaseUrlHasNoTrailingSlash() {
        CreateBundleResult result = new CreateBundleResult(
                "tok123", Instant.parse("2026-09-02T00:00:00Z"), "claude", "global",
                "http://localhost:18080/skillhub");

        CreateBundleResponse response = CreateBundleResponse.from(result);

        assertThat(response.powerShellCommand()).isEqualTo("irm http://localhost:18080/skillhub/i/tok123.ps1 | iex");
        assertThat(response.bashCommand()).isEqualTo("curl -fsSL http://localhost:18080/skillhub/i/tok123.sh | sh");
    }

    @Test
    void should_stripTrailingSlash_when_publicBaseUrlEndsWithSlash() {
        CreateBundleResult result = new CreateBundleResult(
                "tok123", Instant.parse("2026-09-02T00:00:00Z"), "claude", "global",
                "http://localhost:18080/skillhub/");

        CreateBundleResponse response = CreateBundleResponse.from(result);

        assertThat(response.powerShellCommand()).isEqualTo("irm http://localhost:18080/skillhub/i/tok123.ps1 | iex");
        assertThat(response.bashCommand()).isEqualTo("curl -fsSL http://localhost:18080/skillhub/i/tok123.sh | sh");
    }
}
