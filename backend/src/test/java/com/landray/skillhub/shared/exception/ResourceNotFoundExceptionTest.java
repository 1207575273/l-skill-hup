package com.landray.skillhub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("固定映射为 HTTP 404,不受调用方影响")
    void should_alwaysMapToHttp404_when_constructed() {
        ResourceNotFoundException ex = new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在");

        assertThat(ex.httpStatus()).isEqualTo(404);
        assertThat(ex.code()).isEqualTo("SKILL_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("skill 不存在");
    }

    @Test
    @DisplayName("带 details 构造时,details 原样透出")
    void should_carryDetails_when_detailsGiven() {
        ResourceNotFoundException ex =
                new ResourceNotFoundException("SKILL_NOT_FOUND", "skill 不存在", Map.of("skillId", 99L));

        assertThat(ex.details()).containsEntry("skillId", 99L);
    }
}
