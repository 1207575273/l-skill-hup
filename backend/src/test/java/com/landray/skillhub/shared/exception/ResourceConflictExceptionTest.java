package com.landray.skillhub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResourceConflictExceptionTest {

    @Test
    @DisplayName("固定映射为 HTTP 409,不受调用方影响")
    void should_alwaysMapToHttp409_when_constructed() {
        ResourceConflictException ex = new ResourceConflictException("SKILL_NAME_DUPLICATED", "已存在同名 skill");

        assertThat(ex.httpStatus()).isEqualTo(409);
        assertThat(ex.code()).isEqualTo("SKILL_NAME_DUPLICATED");
        assertThat(ex.getMessage()).isEqualTo("已存在同名 skill");
    }

    @Test
    @DisplayName("带 details 构造时,details 原样透出——对应业务建模文档 6.1 的重名冲突示例")
    void should_carryDetails_when_detailsGiven() {
        ResourceConflictException ex =
                new ResourceConflictException("SKILL_NAME_DUPLICATED", "已存在同名 skill", Map.of("skillId", 42L));

        assertThat(ex.details()).containsEntry("skillId", 42L);
    }
}
