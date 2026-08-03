package com.landray.skillhub.shared.logging;

/**
 * 敏感值打日志前的脱敏工具(backend/CLAUDE.md 6.5:"token / 密钥类只打前 8 位 + 长度")。
 *
 * <p>放在 {@code shared} 而不是就地在某个类里写一次:6.5 明确要求"清洗统一走 shared 里的工具方法,不要每处自己写"
 * ——install_bundle 的 token 是本项目第二个需要脱敏打日志的令牌类值(第一个是会话 token,但那个从不打日志,
 * 只在响应体里出现一次),值得有一个可复用的实现而不是各写一份。
 */
public final class SensitiveValueMasker {

    private static final int VISIBLE_PREFIX_LENGTH = 8;

    private SensitiveValueMasker() {}

    /**
     * @return 形如 {@code a1b2c3d4...(len=64)} 的脱敏结果;{@code rawValue} 为 {@code null} 时返回 {@code "null"}
     *     字面量,不抛异常——日志代码不应因为一个空值而中断主流程。
     */
    public static String mask(String rawValue) {
        if (rawValue == null) {
            return "null";
        }
        if (rawValue.length() <= VISIBLE_PREFIX_LENGTH) {
            return "***(len=" + rawValue.length() + ")";
        }
        return rawValue.substring(0, VISIBLE_PREFIX_LENGTH) + "...(len=" + rawValue.length() + ")";
    }
}
