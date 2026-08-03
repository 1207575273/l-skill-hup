package com.landray.skillhub.application.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class BootstrapAdminRunnerTest {

    @Test
    void should_delegateToAuthApplicationServiceWithConfiguredCredentials_when_runCalled() {
        AuthApplicationService authApplicationService = mock(AuthApplicationService.class);
        AuthProperties properties =
                new AuthProperties(false, new AuthProperties.BootstrapAdmin("admin", "admin-pass"));
        BootstrapAdminRunner runner = new BootstrapAdminRunner(authApplicationService, properties);

        runner.run(new DefaultApplicationArguments());

        verify(authApplicationService).ensureBootstrapAdmin("admin", "admin-pass");
    }
}
