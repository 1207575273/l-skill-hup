package com.landray.skillhub.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * 安装级别(业务建模文档 4.13 / 5.9)。
 *
 * <p>{@code apiCode} 同 {@link ClientCode}——小写字符串是 {@code install_bundle.scope} 列与接口的值域,
 * 不跟随 Java 命名习惯改表。
 */
public enum InstallScope {
    /** 落盘到 {@code $HOME/<skillsSubDir>}。 */
    GLOBAL("global"),
    /** 落盘到用户运行安装脚本时的当前工作目录下 {@code <skillsSubDir>}——服务端不知道、也不需要知道具体路径。 */
    PROJECT("project");

    private final String apiCode;

    InstallScope(String apiCode) {
        this.apiCode = apiCode;
    }

    public String apiCode() {
        return apiCode;
    }

    public static Optional<InstallScope> fromApiCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (InstallScope scope : values()) {
            if (scope.apiCode.equals(normalized)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
