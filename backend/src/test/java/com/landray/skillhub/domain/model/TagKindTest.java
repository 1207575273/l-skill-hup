package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.InvalidTagKindException;

class TagKindTest {

    @Test
    @DisplayName("大小写不敏感地解析 curated")
    void should_parseCurated_caseInsensitively() {
        assertThat(TagKind.fromParam("curated")).isEqualTo(TagKind.CURATED);
        assertThat(TagKind.fromParam("CURATED")).isEqualTo(TagKind.CURATED);
    }

    @Test
    @DisplayName("解析 free")
    void should_parseFree() {
        assertThat(TagKind.fromParam("free")).isEqualTo(TagKind.FREE);
    }

    @Test
    @DisplayName("不支持的类型应抛出携带 TAG_KIND_INVALID 错误码的业务异常")
    void should_throwWithBusinessCode_when_paramIsUnsupported() {
        assertThatThrownBy(() -> TagKind.fromParam("SYSTEM"))
                .isInstanceOf(InvalidTagKindException.class)
                .satisfies(ex -> assertThat(((InvalidTagKindException) ex).code()).isEqualTo("TAG_KIND_INVALID"));
    }
}
