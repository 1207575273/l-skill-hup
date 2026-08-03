package com.landray.skillhub.application.upload;

import java.util.List;

/**
 * {@code POST /skills/precheck} 的用例输出(业务建模文档 6.2)。不落库,只回答"这个包能不能发、以什么名字发"。
 *
 * <p>{@code valid=false} 时 {@link #parsed()} / {@link #contentDigest()} 等字段全部为 {@code null}——校验没通过,
 * 谈不上"解析出了什么"。{@link #violations()} 恒非空(装着导致 {@code valid=false} 的那条规则)。
 */
public record PrecheckResult(
        boolean valid,
        ParsedInfo parsed,
        String contentDigest,
        Integer fileCount,
        Long unpackedBytes,
        Boolean hasReadme,
        IdenticalMatch identical,
        boolean duplicatedName,
        ExistingSkillInfo existing,
        String suggestedName,
        List<Violation> violations) {

    public record ParsedInfo(String name, String description, List<String> allowedTools) {}

    /** {@code content_digest} 命中已有版本,即"完全相同的包已存在"(业务建模文档 5.5)。 */
    public record IdenticalMatch(long skillId, String version) {}

    public record ExistingSkillInfo(
            long skillId,
            String name,
            String ownerDisplayName,
            String ownerEmployeeNo,
            String latestVersion,
            boolean canPublishNewVersion) {}

    public record Violation(String code, String message) {}

    public static PrecheckResult invalid(String code, String message) {
        return new PrecheckResult(
                false, null, null, null, null, null, null, false, null, null, List.of(new Violation(code, message)));
    }
}
