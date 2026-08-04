package com.landray.skillhub.interfaces.rest.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /skills/{id}/versions/{version}/certify} 的请求体(业务建模文档 6.2)。
 *
 * <p><b>刻意不在这里用 {@code @Pattern} 卡三个枚举值,也不做"REVOKED 必填 note"的跨字段校验。</b>
 * 那两条规则的唯一事实源是 {@code CertificationDecision} —— 注解能表达前者、表达不了后者(跨字段要上
 * {@code @AssertTrue} 或自定义校验器),于是规则会一半在注解里一半在领域里,而这种一半一半的下场是改一处
 * 忘一处。这里只留一个长度粗筛,把明显离谱的请求体挡在外面。
 */
public record CertifyVersionRequest(
        @NotBlank(message = "认证状态不能为空") String certification,
        @Size(max = 512, message = "认证意见过长") String note) {}
