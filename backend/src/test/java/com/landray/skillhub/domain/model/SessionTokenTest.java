package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionTokenTest {

    @Test
    void should_generateNonBlankRawValueAndHash_when_generateCalled() {
        SessionToken token = SessionToken.generate();

        assertThat(token.rawValue()).isNotBlank();
        assertThat(token.hash()).isNotBlank();
        // sha256 十六进制固定 64 个字符,与 user_session.token_hash CHAR(64) 对齐
        assertThat(token.hash()).hasSize(64);
    }

    @Test
    void should_generateDifferentRawValues_when_calledTwice() {
        SessionToken first = SessionToken.generate();
        SessionToken second = SessionToken.generate();

        assertThat(first.rawValue()).isNotEqualTo(second.rawValue());
        assertThat(first.hash()).isNotEqualTo(second.hash());
    }

    @Test
    void should_produceSameHash_when_hashOfCalledWithSameRawValue() {
        SessionToken token = SessionToken.generate();

        String recomputed = SessionToken.hashOf(token.rawValue());

        assertThat(recomputed).isEqualTo(token.hash());
    }

    @Test
    void should_throw_when_hashOfCalledWithBlankValue() {
        assertThatThrownBy(() -> SessionToken.hashOf(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_constructedWithBlankRawValue() {
        assertThatThrownBy(() -> new SessionToken("", "hash")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_constructedWithBlankHash() {
        assertThatThrownBy(() -> new SessionToken("raw", " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
