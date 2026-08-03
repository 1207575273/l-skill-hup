package com.landray.skillhub.interfaces.rest.skill;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /skills/{id}/update}:改标签 / 来源 / 展示名(owner / ADMIN)。
 *
 * <p><b>刻意没有 {@code name} 字段</b>——业务建模文档 5.16:{@code skill.name} 一经创建不可修改,不给它留一个
 * 请求字段,从结构上就杜绝"漏做校验导致被改名"这类回归,不需要在 application 层再写一条"忽略这个字段"的分支。
 */
public record UpdateSkillRequest(
        @NotBlank(message = "展示名不能为空") @Size(max = 128, message = "展示名长度不得超过 128 个字符") String displayName,
        @NotBlank(message = "来源不能为空") String source,
        @NotEmpty(message = "CURATED 标签数量必须在 1~3 之间") List<Long> curatedTagIds,
        List<String> freeTagNames) {}
