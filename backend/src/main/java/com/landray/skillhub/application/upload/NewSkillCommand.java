package com.landray.skillhub.application.upload;

import java.util.List;

/**
 * {@code POST /skills} 的用例入参(业务建模文档 6.2)。
 *
 * <p>{@code displayName} 允许为空——业务建模文档没有明确规定"发布新 skill 时展示名从哪来"(frontmatter
 * 规范没有这个键,见业务建模文档待验证项 7 附近的讨论范围),这是本任务自行决定的一条规则:留空时回退成最终
 * name(见 {@code SkillUploadApplicationService}),不阻塞发布流程。
 *
 * <p>{@code source} / {@code version} 刻意是原始字符串,不是已解析的 {@link com.landray.skillhub.domain.model.SkillSource}
 * / {@link com.landray.skillhub.domain.model.SemVer}——"把外部输入解释成合法值"是 application 层职责,与
 * {@code SkillApplicationService#listSkills} 对 {@code sourceParam} 的处理方式一致。
 */
public record NewSkillCommand(
        byte[] zipBytes,
        String version,
        String displayName,
        String source,
        List<Long> curatedTagIds,
        List<String> freeTagNames,
        long actingUserId) {}
