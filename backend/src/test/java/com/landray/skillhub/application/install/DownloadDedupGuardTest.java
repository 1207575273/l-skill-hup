package com.landray.skillhub.application.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 直接实例化,不起 Spring——{@link DownloadDedupGuard} 是无外部依赖的纯内存组件(参照 LocalFsArtifactStorageTest 的测法)。 */
class DownloadDedupGuardTest {

    private final DownloadDedupGuard guard = new DownloadDedupGuard();

    @Test
    void should_countFirstRequest_when_noPriorRecordExists() {
        assertThat(guard.shouldCount("10.0.0.1", 1L, Instant.parse("2026-08-03T00:00:00Z"))).isTrue();
    }

    @Test
    void should_notCountAgain_when_withinTenMinuteWindow() {
        Instant first = Instant.parse("2026-08-03T00:00:00Z");
        guard.shouldCount("10.0.0.1", 1L, first);

        boolean secondResult = guard.shouldCount("10.0.0.1", 1L, first.plusSeconds(59));

        assertThat(secondResult).isFalse();
    }

    @Test
    void should_countAgain_when_windowHasElapsed() {
        Instant first = Instant.parse("2026-08-03T00:00:00Z");
        guard.shouldCount("10.0.0.1", 1L, first);

        boolean afterWindow = guard.shouldCount("10.0.0.1", 1L, first.plus(DownloadDedupGuard.WINDOW).plusSeconds(1));

        assertThat(afterWindow).isTrue();
    }

    @Test
    void should_countIndependently_when_versionIdDiffers() {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        guard.shouldCount("10.0.0.1", 1L, now);

        boolean otherVersion = guard.shouldCount("10.0.0.1", 2L, now);

        assertThat(otherVersion).isTrue();
    }

    @Test
    void should_countIndependently_when_clientIpDiffers() {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        guard.shouldCount("10.0.0.1", 1L, now);

        boolean otherIp = guard.shouldCount("10.0.0.2", 1L, now);

        assertThat(otherIp).isTrue();
    }

    @Test
    void should_treatNullOrBlankClientIpAsSameBucket_when_ipUnavailable() {
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        guard.shouldCount(null, 1L, now);

        boolean blankIp = guard.shouldCount("", 1L, now.plusSeconds(1));

        assertThat(blankIp).isFalse();
    }
}
