package com.landray.skillhub.application.install;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code skillhub.install.*} 配置。
 *
 * <p>放在 application 而不是 infrastructure/config——与 {@code AuthProperties} 同样的理由:它承载的是业务配置,
 * 被 {@link InstallApplicationService} 这个 application 层用例编排直接消费。
 *
 * <p>{@code publicBaseUrl} 是渲染安装脚本时拼接下载 URL 的基地址(含 context-path,例如
 * {@code http://intranet-host:18080/skillhub})——脚本运行在用户本机,不在服务端进程里,不能从
 * {@code server.port}/{@code context-path} 反推,必须显式配置。dev 给一个开箱即用的默认值,prod 不留默认值
 * (与 {@code skillhub.storage.local-root} 在 prod 下的处理方式一致)。
 *
 * <p>{@code bundleTtlDays} 是安装单的有效期天数,默认 30——防止旧命令在群里流传半年后还在装过期版本
 * (根 CLAUDE.md 第七节)。业务建模文档 4.13 只定了"必须有有效期"没给具体数值,30 天是本轮实现按
 * "团队内分享通常几周内消化完"与文档 4.13 里"清理 30 天前过期行"的既有节奏对齐选定的,详见
 * {@link InstallApplicationService} 类注释。
 */
@ConfigurationProperties(prefix = "skillhub.install")
public record InstallProperties(String publicBaseUrl, int bundleTtlDays) {}
