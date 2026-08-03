package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class InstallBundleTokenGeneratorTest {

    private static final int VARCHAR_COLUMN_WIDTH = 32;

    @Test
    void should_generateUrlSafeTokenWithinColumnWidth_when_called() {
        String token = InstallBundleTokenGenerator.generate();

        assertThat(token).isNotBlank();
        assertThat(token.length()).isLessThanOrEqualTo(VARCHAR_COLUMN_WIDTH);
        assertThat(token).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void should_generateDistinctTokens_when_calledRepeatedly() {
        Set<String> tokens = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> tokens.add(InstallBundleTokenGenerator.generate()));

        assertThat(tokens).hasSize(1000);
    }
}
