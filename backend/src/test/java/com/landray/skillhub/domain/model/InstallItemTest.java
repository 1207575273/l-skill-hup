package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstallItemTest {

    @Test
    void should_constructSuccessfully_when_bothIdsProvided() {
        InstallItem item = new InstallItem(1L, 10L);

        assertThat(item.skillId()).isEqualTo(1L);
        assertThat(item.versionId()).isEqualTo(10L);
    }

    @Test
    void should_throw_when_skillIdIsNull() {
        assertThatThrownBy(() -> new InstallItem(null, 10L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_versionIdIsNull() {
        assertThatThrownBy(() -> new InstallItem(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
