package com.landray.skillhub.application.upload;

import com.landray.skillhub.application.auth.AuthenticatedUser;

/**
 * {@code POST /skills/{id}/versions} 的用例入参(业务建模文档 6.2)。标签 / 来源 / 展示名不在这里——那是 skill 级属性。
 *
 * <p>携带完整的 {@link AuthenticatedUser} 而不是裸 {@code long actingUserId}——发布新版本要求 owner 或 ADMIN
 * (业务建模文档 5.16),这是一条资源级权限判断,{@code SkillUploadApplicationService} 需要
 * {@link AuthenticatedUser#isAdmin()} 才能做这个判断,只传 id 不够。
 */
public record NewVersionCommand(byte[] zipBytes, String version, AuthenticatedUser actingUser) {}
