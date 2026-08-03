package com.landray.skillhub.shared.logging;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class AuditLogTest {

    @Test
    void should_notThrow_when_loggingEventWithArgs() {
        AuditLog auditLog = new AuditLog();

        assertThatCode(() -> auditLog.info(
                        "skill 上传完成, event={}, skillId={}, skillName={}", "skill_uploaded", 1L, "code-review"))
                .doesNotThrowAnyException();
    }
}
