package com.landray.skillhub.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 纯领域侧单测:不起 Spring,直接验证 BusinessException 基类的字段承载逻辑。 */
class BusinessExceptionTest {

    private static final String CODE = "TEST_ERROR";
    private static final String MESSAGE = "测试用异常消息";

    @Test
    @DisplayName("只传 code/message 时,httpStatus 默认 400,details 默认空表")
    void should_useDefaultHttpStatusAndEmptyDetails_when_onlyCodeAndMessageGiven() {
        TestBusinessException ex = new TestBusinessException(CODE, MESSAGE);

        assertThat(ex.code()).isEqualTo(CODE);
        assertThat(ex.getMessage()).isEqualTo(MESSAGE);
        assertThat(ex.httpStatus()).isEqualTo(BusinessException.DEFAULT_HTTP_STATUS);
        assertThat(ex.details()).isEmpty();
    }

    @Test
    @DisplayName("显式指定 httpStatus 时,取显式值而不是默认值")
    void should_useGivenHttpStatus_when_httpStatusExplicitlySpecified() {
        TestBusinessException ex = new TestBusinessException(CODE, MESSAGE, 404);

        assertThat(ex.httpStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("传入 details 时,原样承载且内容可读")
    void should_carryDetails_when_detailsGiven() {
        TestBusinessException ex = new TestBusinessException(CODE, MESSAGE, 409, Map.of("skillId", 42));

        assertThat(ex.details()).containsEntry("skillId", 42);
    }

    @Test
    @DisplayName("details 不可变,外部尝试修改要抛异常——防止调用方拿到引用后污染异常内部状态")
    void should_rejectMutation_when_detailsAccessedAfterConstruction() {
        TestBusinessException ex = new TestBusinessException(CODE, MESSAGE, 409, Map.of("skillId", 42));

        assertThatThrownBy(() -> ex.details().put("skillId", 99))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("传入 null details 时,不抛异常,返回空表而不是 null")
    void should_returnEmptyMap_when_detailsIsNull() {
        TestBusinessException ex = new TestBusinessException(CODE, MESSAGE, 400, null);

        assertThat(ex.details()).isNotNull().isEmpty();
    }

    /** 仅用于测试基类行为的测试替身,不代表任何真实业务场景。 */
    private static final class TestBusinessException extends BusinessException {

        TestBusinessException(String code, String message) {
            super(code, message);
        }

        TestBusinessException(String code, String message, int httpStatus) {
            super(code, message, httpStatus);
        }

        TestBusinessException(String code, String message, int httpStatus, Map<String, Object> details) {
            super(code, message, httpStatus, details);
        }
    }
}
