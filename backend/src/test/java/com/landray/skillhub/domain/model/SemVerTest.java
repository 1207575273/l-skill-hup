package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.InvalidSkillVersionException;

class SemVerTest {

    @Test
    @DisplayName("合法的 major.minor.patch 能正确解析")
    void should_parseVersion_when_formatIsValid() {
        SemVer version = SemVer.parse("1.2.3");

        assertThat(version.major()).isEqualTo(1);
        assertThat(version.minor()).isEqualTo(2);
        assertThat(version.patch()).isEqualTo(3);
        assertThat(version.raw()).isEqualTo("1.2.3");
    }

    @Test
    @DisplayName("0.0.0 是合法的首个版本")
    void should_parseVersion_when_allZero() {
        assertThat(SemVer.parse("0.0.0")).isNotNull();
    }

    @Test
    @DisplayName("为空时拒绝")
    void should_reject_when_versionIsBlank() {
        assertThatThrownBy(() -> SemVer.parse(" ")).isInstanceOf(InvalidSkillVersionException.class);
    }

    @Test
    @DisplayName("段数不是三段时拒绝")
    void should_reject_when_notThreeSegments() {
        assertThatThrownBy(() -> SemVer.parse("1.2")).isInstanceOf(InvalidSkillVersionException.class);
        assertThatThrownBy(() -> SemVer.parse("1.2.3.4")).isInstanceOf(InvalidSkillVersionException.class);
    }

    @Test
    @DisplayName("带前导零时拒绝")
    void should_reject_when_hasLeadingZero() {
        assertThatThrownBy(() -> SemVer.parse("01.2.3")).isInstanceOf(InvalidSkillVersionException.class);
    }

    @Test
    @DisplayName("带预发布标识或构建元数据时拒绝——本项目不支持")
    void should_reject_when_hasPrereleaseOrBuildMetadata() {
        assertThatThrownBy(() -> SemVer.parse("1.2.3-alpha")).isInstanceOf(InvalidSkillVersionException.class);
        assertThatThrownBy(() -> SemVer.parse("1.2.3+build.1")).isInstanceOf(InvalidSkillVersionException.class);
    }

    @Test
    @DisplayName("负数段时拒绝")
    void should_reject_when_negativeSegment() {
        assertThatThrownBy(() -> SemVer.parse("-1.2.3")).isInstanceOf(InvalidSkillVersionException.class);
    }

    @Test
    @DisplayName("非数字段时拒绝")
    void should_reject_when_notNumeric() {
        assertThatThrownBy(() -> SemVer.parse("a.b.c")).isInstanceOf(InvalidSkillVersionException.class);
    }

    @Test
    @DisplayName("数值序比较,10.0.0 大于 9.0.0——不能退化成字符串序")
    void should_compareNumerically_notLexically() {
        SemVer ten = SemVer.parse("10.0.0");
        SemVer nine = SemVer.parse("9.0.0");

        assertThat(ten.isGreaterThan(nine)).isTrue();
        assertThat(nine.isGreaterThan(ten)).isFalse();
        assertThat(ten.compareTo(nine)).isPositive();
    }

    @Test
    @DisplayName("minor 段决定大小,当 major 相同时")
    void should_compareByMinor_when_majorEqual() {
        assertThat(SemVer.parse("1.2.0").isGreaterThan(SemVer.parse("1.1.9"))).isTrue();
    }

    @Test
    @DisplayName("patch 段决定大小,当 major/minor 都相同时")
    void should_compareByPatch_when_majorAndMinorEqual() {
        assertThat(SemVer.parse("1.1.2").isGreaterThan(SemVer.parse("1.1.1"))).isTrue();
    }

    @Test
    @DisplayName("相等版本互不大于")
    void should_notBeGreater_when_versionsEqual() {
        assertThat(SemVer.parse("1.2.3").isGreaterThan(SemVer.parse("1.2.3"))).isFalse();
    }

    @Test
    @DisplayName("toString 返回原始字符串")
    void should_returnRawString_when_toString() {
        assertThat(SemVer.parse("1.2.3").toString()).isEqualTo("1.2.3");
    }
}
