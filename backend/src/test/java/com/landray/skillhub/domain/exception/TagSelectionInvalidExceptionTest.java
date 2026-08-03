package com.landray.skillhub.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TagSelectionInvalidExceptionTest {

    @Test
    void should_mapToHttp400WithGivenMessage_when_constructed() {
        TagSelectionInvalidException ex = new TagSelectionInvalidException("CURATED 标签数量必须在 1~3 之间");

        assertThat(ex.httpStatus()).isEqualTo(400);
        assertThat(ex.code()).isEqualTo("TAG_SELECTION_INVALID");
        assertThat(ex.getMessage()).isEqualTo("CURATED 标签数量必须在 1~3 之间");
    }
}
