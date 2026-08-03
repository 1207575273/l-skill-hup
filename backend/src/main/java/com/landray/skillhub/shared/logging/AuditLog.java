package com.landray.skillhub.shared.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 关键业务事件的统一出口,固定 logger name {@code skillhub.audit}(backend/CLAUDE.md 6.6)。
 *
 * <p>不现在单独做审计系统,将来要审计直接从 JSONL 捞——这个承诺成立的前提是"事件都打在同一个可过滤的 logger
 * 分类下",而不是散落在各业务类各自的 class logger 里、只能靠肉眼从一堆操作日志中挑出来。
 *
 * <p><b>为什么单独建一个类,而不是让每个 application 服务自己再声明一个 Logger 字段</b>:
 * {@code LoggingConventionArchitectureTest} 强制"每个 {@code org.slf4j.Logger} 字段必须命名为
 * {@code log}"——一个类里只能有一个名叫 {@code log} 的字段,没法同时挂"这个类自己的 class logger"和"审计
 * logger"两个。把审计 logger 收敛成一个可注入的组件,调用方(如
 * {@code SkillUploadApplicationService})正常声明自己的 {@code log} 字段打常规日志,再注入这个组件打审计
 * 事件,两条通道不冲突。
 *
 * <p>只暴露 {@code info} 一个方法:审计事件的判定标准是"发生了、需要能追溯",不是级别语义(backend/CLAUDE.md
 * 6.6 明确认证状态变更这类**安全事件**要 WARN 起步——那类调用方仍然用自己的 class logger 打 WARN,同时也可以
 * 调这里的 {@code info} 记一条可检索的审计事件,两者不是互斥关系)。
 */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("skillhub.audit");

    public void info(String message, Object... args) {
        log.info(message, args);
    }
}
