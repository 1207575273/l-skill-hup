package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.InvalidInstallBundleException;

class InstallBundleTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void should_generateTokenAndComputeExpiresAt_when_created() {
        InstallBundle bundle = InstallBundle.create(
                List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, 5L, NOW,
                Duration.ofDays(30));

        assertThat(bundle.id()).isNull();
        assertThat(bundle.token()).isNotBlank();
        assertThat(bundle.createdAt()).isEqualTo(NOW);
        assertThat(bundle.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(bundle.createdByUserId()).isEqualTo(5L);
    }

    @Test
    void should_allowNullCreatedByUserId_when_anonymousUserCreatesBundle() {
        InstallBundle bundle = InstallBundle.create(
                List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null, NOW,
                Duration.ofDays(30));

        assertThat(bundle.createdByUserId()).isNull();
    }

    @Test
    void should_throw_when_itemsIsEmpty() {
        assertThatThrownBy(() -> new InstallBundle(
                        null, "token-value", List.of(), ClientCode.CLAUDE, InstallScope.GLOBAL, null, NOW,
                        NOW.plusSeconds(1)))
                .isInstanceOf(InvalidInstallBundleException.class);
    }

    @Test
    void should_throw_when_itemsExceedsMaxSize() {
        List<InstallItem> tooMany = java.util.stream.LongStream.range(0, InstallBundle.MAX_ITEMS + 1)
                .mapToObj(i -> new InstallItem(i, i))
                .toList();

        assertThatThrownBy(() -> new InstallBundle(
                        null, "token-value", tooMany, ClientCode.CLAUDE, InstallScope.GLOBAL, null, NOW,
                        NOW.plusSeconds(1)))
                .isInstanceOf(InvalidInstallBundleException.class);
    }

    @Test
    void should_makeItemsImmutable_when_constructed() {
        InstallBundle bundle = new InstallBundle(
                null, "token-value", List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null,
                NOW, NOW.plusSeconds(1));

        assertThatThrownBy(() -> bundle.items().add(new InstallItem(2L, 20L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_throw_when_tokenIsBlank() {
        assertThatThrownBy(() -> new InstallBundle(
                        null, " ", List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null,
                        NOW, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_beExpired_when_nowIsAtOrAfterExpiresAt() {
        InstallBundle bundle = new InstallBundle(
                1L, "token-value", List.of(new InstallItem(1L, 10L)), ClientCode.CLAUDE, InstallScope.GLOBAL, null,
                NOW, NOW.plusSeconds(60));

        assertThat(bundle.isExpired(NOW.plusSeconds(60))).isTrue();
        assertThat(bundle.isExpired(NOW.plusSeconds(61))).isTrue();
        assertThat(bundle.isExpired(NOW.plusSeconds(59))).isFalse();
    }

    @Test
    void should_notMutateCallerList_when_callerListIsModifiedAfterConstruction() {
        List<InstallItem> mutable = new java.util.ArrayList<>(List.of(new InstallItem(1L, 10L)));
        InstallBundle bundle = new InstallBundle(
                null, "token-value", mutable, ClientCode.CLAUDE, InstallScope.GLOBAL, null, NOW, NOW.plusSeconds(1));

        mutable.add(new InstallItem(2L, 20L));

        assertThat(bundle.items()).hasSize(1);
        assertThat(Collections.unmodifiableList(bundle.items())).containsExactly(new InstallItem(1L, 10L));
    }
}
