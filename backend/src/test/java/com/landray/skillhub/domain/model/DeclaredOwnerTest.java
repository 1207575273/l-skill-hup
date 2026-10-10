package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DeclaredOwner} 的构造约束与"没有声明"这个状态的归一化。
 *
 * <p>重点在 {@link DeclaredOwner#ofNullable} 上:匿名上传的两个表单字段是可选的,"都没填"与"填了空格"必须
 * 落到同一个状态(整体为 {@code null}),否则前端会多出一种"有 declaredOwner 对象但里面是空的"的半残情形要特判。
 */
class DeclaredOwnerTest {

    @Test
    @DisplayName("姓名与工号都填了:原样保留")
    void should_keepBothFields_when_bothProvided() {
        DeclaredOwner owner = DeclaredOwner.ofNullable("张三", "80380486");

        assertThat(owner).isEqualTo(new DeclaredOwner("张三", "80380486"));
    }

    @Test
    @DisplayName("首尾空白在构造工厂里去掉,不留给调用方各写一遍 trim")
    void should_stripWhitespace_when_fieldsPadded() {
        DeclaredOwner owner = DeclaredOwner.ofNullable("  张三  ", "  80380486  ");

        assertThat(owner).isEqualTo(new DeclaredOwner("张三", "80380486"));
    }

    @Test
    @DisplayName("只填姓名不填工号:合法,工号为 null")
    void should_allowMissingEmployeeNo_when_onlyNameProvided() {
        DeclaredOwner owner = DeclaredOwner.ofNullable("张三", null);

        assertThat(owner.name()).isEqualTo("张三");
        assertThat(owner.employeeNo()).isNull();
    }

    @Test
    @DisplayName("工号填了空白:等同于没填")
    void should_treatBlankEmployeeNoAsAbsent_when_onlyWhitespace() {
        assertThat(DeclaredOwner.ofNullable("张三", "   ").employeeNo()).isNull();
    }

    @Test
    @DisplayName("什么都没填:整体为 null,而不是一个两格都空的实例")
    void should_returnNull_when_nothingDeclared() {
        assertThat(DeclaredOwner.ofNullable(null, null)).isNull();
        assertThat(DeclaredOwner.ofNullable("", "")).isNull();
        assertThat(DeclaredOwner.ofNullable("   ", "   ")).isNull();
    }

    @Test
    @DisplayName("只填工号不填姓名:视为没有声明 —— 一个光秃秃的工号对旁人判断没有帮助")
    void should_returnNull_when_onlyEmployeeNoProvided() {
        assertThat(DeclaredOwner.ofNullable(null, "80380486")).isNull();
        assertThat(DeclaredOwner.ofNullable("  ", "80380486")).isNull();
    }

    @Test
    @DisplayName("直接构造时姓名不许为空:空值应当走 ofNullable 落到 null,而不是造一个空实例")
    void should_reject_when_nameBlankInDirectConstruction() {
        assertThatThrownBy(() -> new DeclaredOwner("  ", "80380486"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> new DeclaredOwner(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("姓名超过列宽(64)时拒绝 —— 兜底防御,入口还有 Bean Validation")
    void should_reject_when_nameExceedsColumnWidth() {
        String tooLong = "张".repeat(DeclaredOwner.MAX_NAME_LENGTH + 1);

        assertThatThrownBy(() -> new DeclaredOwner(tooLong, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得超过 64");
    }

    @Test
    @DisplayName("工号超过列宽(32)时拒绝")
    void should_reject_when_employeeNoExceedsColumnWidth() {
        String tooLong = "8".repeat(DeclaredOwner.MAX_EMPLOYEE_NO_LENGTH + 1);

        assertThatThrownBy(() -> new DeclaredOwner("张三", tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得超过 32");
    }

    @Test
    @DisplayName("列宽常量与 app_user 对齐:两边描述的是同一种东西,宽度不一致会凭空多出截断风险")
    void should_matchAppUserColumnWidths_forConsistency() {
        assertThat(DeclaredOwner.MAX_NAME_LENGTH).isEqualTo(AppUser.MAX_DISPLAY_NAME_LENGTH);
    }
}
