package com.landray.skillhub.application.upload;

import java.util.List;

import com.landray.skillhub.domain.model.DeclaredOwner;

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
 *
 * <p>{@code actingUserId} 对**匿名上传**取 {@link com.landray.skillhub.domain.model.AppUser#SYSTEM_USER_ID}
 * ——上传不再要求登录(见 {@code SkillController})。它同时是 skill 的归属人、首个版本的发布者与审计里的操作者,
 * 三者都落在 system 名下,于是这个 skill 处于"无主待认领"状态({@code Skill#isUnclaimed()})。
 *
 * <p>{@code declaredOwner} 只在匿名上传时允许有值:那是上传者自称的归属,是旁人判断"这大概是谁的包"的唯一
 * 线索。已登录上传时归属人就是上传者本人,再带一份自称归属会被拒(见
 * {@link com.landray.skillhub.domain.exception.DeclaredOwnerNotAllowedException})。
 */
public record NewSkillCommand(
        byte[] zipBytes,
        String version,
        String displayName,
        String source,
        List<Long> curatedTagIds,
        List<String> freeTagNames,
        long actingUserId,
        DeclaredOwner declaredOwner) {}
