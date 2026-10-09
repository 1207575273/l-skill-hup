package com.landray.skillhub.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link ArtifactKeyGuard} 的越界形态逐条过一遍。
 *
 * <p><b>为什么这些用例集中在这里,而不是在两个存储实现的测试里各写一遍</b>:接口
 * {@code domain.storage.ArtifactStorage} 把"拒绝 {@code ..} / 绝对路径 / 控制字符"定成了对所有实现的硬要求。
 * 两个实现现在共用同一段校验代码,所以校验本身只需要在它自己这一层测透;各实现的测试只需要证明
 * "我确实调了它"(典型是 delete 那条 —— 接口注释特别点出删除漏校验等于开了个用删除做目录穿越的口子)。
 *
 * <p>这样拆的好处是:将来补一种新的越界形态,只要在这里加一条用例,两个实现自动都被覆盖,不存在
 * "给其中一个补了、另一个漏了"的漂移空间。
 */
class ArtifactKeyGuardTest {

    @Test
    void should_returnKeyUnchanged_whenKeyIsSafe() {
        // 原样返回是为了能链式用(objectKey 里就是 keyPrefix + requireSafe(key)),
        // 所以"没改动过"本身是契约的一部分,值得断言。
        assertThat(ArtifactKeyGuard.requireSafe("code-review/1.0.0/abc.zip")).isEqualTo("code-review/1.0.0/abc.zip");
    }

    @Test
    void should_rejectKey_whenNull() {
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void should_rejectKey_whenBlank() {
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void should_rejectKey_whenLeadingParentTraversal() {
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("../escaped.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相对路径");
    }

    @Test
    void should_rejectKey_whenTraversalHiddenInMiddle() {
        // 归一化之后仍落在根内,但依然拒绝:放过它就等于承认"只要最终落点没越界就行",
        // 而那个前提一旦被后续改动打破,这里不会有任何提示。
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("a/../b.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相对路径");
    }

    @Test
    void should_rejectKey_whenAbsoluteUnixPath() {
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绝对路径");
    }

    @Test
    void should_rejectKey_whenAbsoluteWindowsPath() {
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("C:\\Windows\\evil.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绝对路径");
    }

    @Test
    void should_rejectKey_whenUncStylePath() {
        // 反斜杠开头:Windows 上的 UNC / 驱动器相对路径形态,与正斜杠一视同仁地拦掉。
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("\\\\server\\share\\evil.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绝对路径");
    }

    @Test
    void should_rejectKey_whenContainingNewline() {
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("a\nb.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("控制字符");
    }

    @Test
    void should_rejectKey_whenContainingNulByte() {
        // NUL 截断是经典绕法:底层若用 C 字符串,"a.zip\0.txt" 会被当成 "a.zip"。
        assertThatThrownBy(() -> ArtifactKeyGuard.requireSafe("a.zip\u0000.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("控制字符");
    }
}
