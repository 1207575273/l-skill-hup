package com.landray.skillhub.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * 客户端适配表:{@code (client, scope) -> 是否已实测}(业务建模文档 5.9)。
 *
 * <p><b>代码里的枚举,不是数据库表</b>——几行数据不值得建表,而且它带"是否已实测"这种代码属性。
 * {@code workbuddy} 的项目级路径未经本地实测(文档待验证项 1),标记为未实测且 {@link #find} 永远不会返回它,
 * 从建模层面就不允许生成一个无法正确渲染的安装单,而不是指望调用方自己记得检查。
 */
public record ClientTarget(ClientCode client, InstallScope scope, boolean tested) {

    private static final List<ClientTarget> ALL = List.of(
            new ClientTarget(ClientCode.CLAUDE, InstallScope.GLOBAL, true),
            new ClientTarget(ClientCode.CLAUDE, InstallScope.PROJECT, true),
            new ClientTarget(ClientCode.CODEX, InstallScope.GLOBAL, true),
            new ClientTarget(ClientCode.CODEX, InstallScope.PROJECT, true),
            new ClientTarget(ClientCode.WORKBUDDY, InstallScope.GLOBAL, true),
            // workbuddy 项目级路径未实测(文档 3.3/十.待验证项 1),不许猜路径,永远不进"已实测"列表。
            new ClientTarget(ClientCode.WORKBUDDY, InstallScope.PROJECT, false));

    /**
     * {@code GET /clients} 只返回已实测的组合。
     *
     * <p>命名 {@code testedTargets} 而不是 {@code tested}——record 的紧凑访问器已经占用了 {@code tested()}
     * 这个名字(读取本实例的 {@code tested} 字段),静态工厂方法不能与实例访问器同名同参数表,否则编译器会把
     * 两者当成同一个签名的重复定义而报错。
     */
    public static List<ClientTarget> testedTargets() {
        return ALL.stream().filter(ClientTarget::tested).toList();
    }

    /**
     * 按接口传入的字符串解析并要求该组合已实测,否则返回空——{@code POST /bundles} 据此判定
     * {@code CLIENT_SCOPE_NOT_SUPPORTED}(文档 5.9)。
     */
    public static Optional<ClientTarget> find(String clientCode, String scopeCode) {
        Optional<ClientCode> client = ClientCode.fromApiCode(clientCode);
        Optional<InstallScope> scope = InstallScope.fromApiCode(scopeCode);
        if (client.isEmpty() || scope.isEmpty()) {
            return Optional.empty();
        }
        return ALL.stream()
                .filter(target -> target.tested()
                        && target.client() == client.get()
                        && target.scope() == scope.get())
                .findFirst();
    }
}
