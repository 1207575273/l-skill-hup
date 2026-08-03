package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClientCodeTest {

    @Test
    void should_resolveCaseInsensitively_when_apiCodeMatchesKnownValue() {
        assertThat(ClientCode.fromApiCode("claude")).contains(ClientCode.CLAUDE);
        assertThat(ClientCode.fromApiCode("CLAUDE")).contains(ClientCode.CLAUDE);
        assertThat(ClientCode.fromApiCode(" codex ")).contains(ClientCode.CODEX);
        assertThat(ClientCode.fromApiCode("workbuddy")).contains(ClientCode.WORKBUDDY);
    }

    @Test
    void should_returnEmpty_when_apiCodeIsUnknownOrBlank() {
        assertThat(ClientCode.fromApiCode("unknown-client")).isEmpty();
        assertThat(ClientCode.fromApiCode(null)).isEmpty();
        assertThat(ClientCode.fromApiCode("  ")).isEmpty();
    }

    @Test
    void should_exposeSkillsSubDirPerClient_when_queried() {
        assertThat(ClientCode.CLAUDE.skillsSubDir()).isEqualTo(".claude/skills");
        assertThat(ClientCode.CODEX.skillsSubDir()).isEqualTo(".codex/skills");
        assertThat(ClientCode.WORKBUDDY.skillsSubDir()).isEqualTo(".workbuddy/skills");
    }

    @Test
    void should_exposeLowercaseApiCode_when_queried() {
        Optional<ClientCode> claude = ClientCode.fromApiCode("claude");

        assertThat(claude).isPresent();
        assertThat(claude.get().apiCode()).isEqualTo("claude");
    }
}
