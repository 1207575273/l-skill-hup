package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.InvalidSkillSourceException;

class SkillSourceTest {

    @Test
    @DisplayName("大小写不敏感地解析 internal")
    void should_parseInternal_caseInsensitively() {
        assertThat(SkillSource.fromParam("internal")).isEqualTo(SkillSource.INTERNAL);
        assertThat(SkillSource.fromParam("INTERNAL")).isEqualTo(SkillSource.INTERNAL);
    }

    @Test
    @DisplayName("解析 community")
    void should_parseCommunity() {
        assertThat(SkillSource.fromParam("community")).isEqualTo(SkillSource.COMMUNITY);
    }

    @Test
    @DisplayName("不支持的来源应抛出携带 SKILL_SOURCE_INVALID 错误码的业务异常")
    void should_throwWithBusinessCode_when_paramIsUnsupported() {
        assertThatThrownBy(() -> SkillSource.fromParam("external"))
                .isInstanceOf(InvalidSkillSourceException.class)
                .satisfies(
                        ex -> assertThat(((InvalidSkillSourceException) ex).code()).isEqualTo("SKILL_SOURCE_INVALID"));
    }
}
