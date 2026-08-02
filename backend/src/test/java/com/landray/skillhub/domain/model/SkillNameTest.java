package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.landray.skillhub.domain.exception.InvalidSkillNameException;

/** 纯领域单测:不起 Spring、不碰 DB,快且稳,是覆盖率主力。 */
class SkillNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "pdf", "skill-hub", "docx-to-md", "v2-parser", "a1-b2-c3", "123"})
    @DisplayName("合法 kebab-case 应被接受")
    void should_accept_when_value_is_valid_kebab_case(String value) {
        assertThat(new SkillName(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("空值应被拒绝")
    void should_reject_when_value_is_blank(String value) {
        assertThatThrownBy(() -> new SkillName(value))
                .isInstanceOf(InvalidSkillNameException.class)
                .hasMessageContaining("不能为空");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "PDF", // 大写
                "skill_hub", // 下划线
                "skill hub", // 空格
                "-skill", // 连字符开头
                "skill-", // 连字符结尾
                "skill--hub", // 连续连字符
                "skill.hub", // 点号
                "skill@hub", // 特殊字符
                "技能" // 非 ASCII
            })
    @DisplayName("不符合 kebab-case 的应被拒绝")
    void should_reject_when_value_violates_kebab_case(String value) {
        assertThatThrownBy(() -> new SkillName(value))
                .isInstanceOf(InvalidSkillNameException.class)
                .hasMessageContaining("kebab-case");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../etc/passwd",
                "..",
                "a/../b",
                "/absolute/path",
                "C:\\windows",
                "a/b",
                "a\\b"
            })
    @DisplayName("路径穿越样式应被拒绝 —— 名称直接参与安装路径拼接,这是安全边界不是命名规范")
    void should_reject_when_value_looks_like_path_traversal(String value) {
        assertThatThrownBy(() -> new SkillName(value))
                .isInstanceOf(InvalidSkillNameException.class);
    }

    @Test
    @DisplayName("超长名称应被拒绝")
    void should_reject_when_value_exceeds_max_length() {
        String tooLong = "a".repeat(65);

        assertThatThrownBy(() -> new SkillName(tooLong))
                .isInstanceOf(InvalidSkillNameException.class)
                .hasMessageContaining("长度");
    }

    @Test
    @DisplayName("恰好达到长度上限应被接受")
    void should_accept_when_value_is_exactly_max_length() {
        String exactly = "a".repeat(64);

        assertThat(new SkillName(exactly).value()).hasSize(64);
    }

    @Test
    @DisplayName("拒绝时应携带业务错误码 —— 前端据此差异化处理,不许依赖 message 文本")
    void should_carry_business_error_code_when_rejected() {
        assertThatThrownBy(() -> new SkillName("BAD_NAME"))
                .isInstanceOf(InvalidSkillNameException.class)
                .satisfies(ex -> assertThat(((InvalidSkillNameException) ex).code())
                        .isEqualTo("SKILL_NAME_INVALID"));
    }

    @Test
    @DisplayName("toString 返回原始值,便于直接拼路径与打日志")
    void should_return_raw_value_when_to_string() {
        assertThat(new SkillName("docx-to-md")).hasToString("docx-to-md");
    }

    @Test
    @DisplayName("值相同即相等 —— record 的值语义")
    void should_be_equal_when_value_is_same() {
        assertThat(new SkillName("pdf")).isEqualTo(new SkillName("pdf"));
    }
}
