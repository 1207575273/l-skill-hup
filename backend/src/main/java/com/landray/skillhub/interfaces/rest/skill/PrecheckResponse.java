package com.landray.skillhub.interfaces.rest.skill;

import java.util.List;

import com.landray.skillhub.application.upload.PrecheckResult;

/** {@code POST /skills/precheck} 响应体,字段与业务建模文档 6.2 的 JSON 示例逐一对应。 */
public record PrecheckResponse(
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

    public record ParsedInfo(String name, String description, List<String> allowedTools) {
        static ParsedInfo from(PrecheckResult.ParsedInfo source) {
            return source == null ? null : new ParsedInfo(source.name(), source.description(), source.allowedTools());
        }
    }

    public record IdenticalMatch(long skillId, String version) {
        static IdenticalMatch from(PrecheckResult.IdenticalMatch source) {
            return source == null ? null : new IdenticalMatch(source.skillId(), source.version());
        }
    }

    public record ExistingSkillInfo(
            long skillId,
            String name,
            String ownerDisplayName,
            String ownerEmployeeNo,
            String latestVersion,
            boolean canPublishNewVersion) {
        static ExistingSkillInfo from(PrecheckResult.ExistingSkillInfo source) {
            if (source == null) {
                return null;
            }
            return new ExistingSkillInfo(
                    source.skillId(),
                    source.name(),
                    source.ownerDisplayName(),
                    source.ownerEmployeeNo(),
                    source.latestVersion(),
                    source.canPublishNewVersion());
        }
    }

    public record Violation(String code, String message) {
        static Violation from(PrecheckResult.Violation source) {
            return new Violation(source.code(), source.message());
        }
    }

    public static PrecheckResponse from(PrecheckResult result) {
        return new PrecheckResponse(
                result.valid(),
                ParsedInfo.from(result.parsed()),
                result.contentDigest(),
                result.fileCount(),
                result.unpackedBytes(),
                result.hasReadme(),
                IdenticalMatch.from(result.identical()),
                result.duplicatedName(),
                ExistingSkillInfo.from(result.existing()),
                result.suggestedName(),
                result.violations().stream().map(Violation::from).toList());
    }
}
