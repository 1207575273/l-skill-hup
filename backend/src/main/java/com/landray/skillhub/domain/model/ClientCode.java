package com.landray.skillhub.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * 目标客户端(业务建模文档 5.9 客户端适配表)。
 *
 * <p>{@code apiCode} 是接口与数据库里使用的小写字符串(如 {@code install_bundle.client}),与 Java 枚举常量名
 * 大小写不一致——数据库列的值域在文档 4.13 里已定死为 {@code claude / codex / workbuddy},不能因为
 * Java 命名习惯反过来改表。{@code skillsSubDir} 是该客户端在落盘根目录下的子路径(不含 skill 名与安装根目录本身),
 * 三个客户端只是这一个字符串不同,新增客户端只加一行(根 CLAUDE.md 第一节)。
 */
public enum ClientCode {
    CLAUDE("claude", ".claude/skills"),
    CODEX("codex", ".codex/skills"),
    WORKBUDDY("workbuddy", ".workbuddy/skills");

    private final String apiCode;
    private final String skillsSubDir;

    ClientCode(String apiCode, String skillsSubDir) {
        this.apiCode = apiCode;
        this.skillsSubDir = skillsSubDir;
    }

    public String apiCode() {
        return apiCode;
    }

    /** 落盘子路径,例如 {@code .claude/skills}——与安装根目录(全局 {@code $HOME},项目级为当前工作目录)拼接。 */
    public String skillsSubDir() {
        return skillsSubDir;
    }

    /** 按接口传入的字符串解析,大小写不敏感;不识别的值返回空,由调用方决定报什么错误。 */
    public static Optional<ClientCode> fromApiCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ClientCode code : values()) {
            if (code.apiCode.equals(normalized)) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }
}
