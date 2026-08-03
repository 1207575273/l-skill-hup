package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Sha256Test {

    @Test
    @DisplayName("空字符串的 sha256 是已知的固定值")
    void should_returnKnownDigest_when_inputIsEmptyString() {
        assertThat(Sha256.hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("结果固定 64 位十六进制小写")
    void should_return64LowercaseHexChars_when_hashingAnyInput() {
        String hex = Sha256.hex("hello world".getBytes(StandardCharsets.UTF_8));

        assertThat(hex).hasSize(64);
        assertThat(hex).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("相同输入产生相同摘要")
    void should_beDeterministic_when_hashingSameInputTwice() {
        assertThat(Sha256.hex("skill-hub")).isEqualTo(Sha256.hex("skill-hub"));
    }

    @Test
    @DisplayName("不同输入产生不同摘要")
    void should_produceDifferentDigest_when_inputsDiffer() {
        assertThat(Sha256.hex("a")).isNotEqualTo(Sha256.hex("b"));
    }
}
