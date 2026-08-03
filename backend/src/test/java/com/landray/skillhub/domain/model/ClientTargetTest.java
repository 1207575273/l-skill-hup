package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 客户端适配表(业务建模文档 5.9)。{@code (workbuddy, project)} 未实测这条断言是本任务的核心约束——
 * 建模层面就不允许生成一个无法正确渲染的安装单。
 */
class ClientTargetTest {

    @Test
    void should_containFiveTestedCombinations_when_listingTestedTargets() {
        List<ClientTarget> tested = ClientTarget.testedTargets();

        assertThat(tested).hasSize(5);
        assertThat(tested).allMatch(ClientTarget::tested);
    }

    @Test
    void should_excludeWorkbuddyProject_when_listingTestedTargets() {
        List<ClientTarget> tested = ClientTarget.testedTargets();

        assertThat(tested)
                .noneMatch(target -> target.client() == ClientCode.WORKBUDDY && target.scope() == InstallScope.PROJECT);
    }

    @Test
    void should_findTarget_when_combinationIsTested() {
        assertThat(ClientTarget.find("claude", "global")).isPresent();
        assertThat(ClientTarget.find("claude", "project")).isPresent();
        assertThat(ClientTarget.find("codex", "global")).isPresent();
        assertThat(ClientTarget.find("codex", "project")).isPresent();
        assertThat(ClientTarget.find("workbuddy", "global")).isPresent();
    }

    @Test
    void should_returnEmpty_when_combinationIsWorkbuddyProject() {
        assertThat(ClientTarget.find("workbuddy", "project")).isEmpty();
    }

    @Test
    void should_returnEmpty_when_clientOrScopeIsUnknown() {
        assertThat(ClientTarget.find("cursor", "global")).isEmpty();
        assertThat(ClientTarget.find("claude", "workspace")).isEmpty();
    }
}
