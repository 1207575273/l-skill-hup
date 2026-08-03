package com.landray.skillhub.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveValueMaskerTest {

    @Test
    void should_keepOnlyFirstEightCharsAndAppendLength_when_valueLongerThanEight() {
        String masked = SensitiveValueMasker.mask("a1b2c3d4e5f6g7h8");

        assertThat(masked).isEqualTo("a1b2c3d4...(len=16)");
    }

    @Test
    void should_notLeakAnyCharacter_when_valueShorterThanOrEqualToEight() {
        String masked = SensitiveValueMasker.mask("short");

        assertThat(masked).isEqualTo("***(len=5)");
        assertThat(masked).doesNotContain("short");
    }

    @Test
    void should_returnNullLiteral_when_valueIsNull() {
        assertThat(SensitiveValueMasker.mask(null)).isEqualTo("null");
    }
}
