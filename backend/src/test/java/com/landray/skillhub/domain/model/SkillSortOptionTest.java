package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.landray.skillhub.domain.exception.InvalidSkillSortException;

class SkillSortOptionTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("未传 sort 时应回落到默认值 LATEST")
    void should_returnDefault_when_paramIsBlank(String raw) {
        assertThat(SkillSortOption.fromParam(raw)).isEqualTo(SkillSortOption.LATEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"downloads", "DOWNLOADS", "Downloads"})
    @DisplayName("大小写不敏感地解析 downloads")
    void should_parseDownloads_caseInsensitively(String raw) {
        assertThat(SkillSortOption.fromParam(raw)).isEqualTo(SkillSortOption.DOWNLOADS);
    }

    @Test
    @DisplayName("解析 likes")
    void should_parseLikes() {
        assertThat(SkillSortOption.fromParam("likes")).isEqualTo(SkillSortOption.LIKES);
    }

    @Test
    @DisplayName("解析 latest")
    void should_parseLatest() {
        assertThat(SkillSortOption.fromParam("latest")).isEqualTo(SkillSortOption.LATEST);
    }

    @Test
    @DisplayName("不支持的排序方式应抛出携带 SKILL_SORT_INVALID 错误码的业务异常")
    void should_throwWithBusinessCode_when_paramIsUnsupported() {
        assertThatThrownBy(() -> SkillSortOption.fromParam("popularity"))
                .isInstanceOf(InvalidSkillSortException.class)
                .satisfies(ex -> assertThat(((InvalidSkillSortException) ex).code()).isEqualTo("SKILL_SORT_INVALID"));
    }
}
