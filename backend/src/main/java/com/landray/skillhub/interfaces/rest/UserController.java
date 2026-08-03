package com.landray.skillhub.interfaces.rest;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.application.auth.UserManagementApplicationService;
import com.landray.skillhub.application.auth.UserWithIdentities;
import com.landray.skillhub.domain.model.AppUser;
import com.landray.skillhub.domain.model.IdentityProvider;
import com.landray.skillhub.interfaces.rest.auth.CurrentUserAccessor;
import com.landray.skillhub.interfaces.rest.auth.RequireAdmin;
import com.landray.skillhub.shared.model.PageResult;

/**
 * 用户管理接口,ADMIN 专属(业务建模文档 6.2"用户管理"一组)。整个 Controller 类标 {@link RequireAdmin},
 * 是否为 ADMIN 由 {@code AuthenticationInterceptor} 统一把关,这里不重复判断角色。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequireAdmin
@Validated
public class UserController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final UserManagementApplicationService userManagementApplicationService;
    private final CurrentUserAccessor currentUserAccessor;

    public UserController(
            UserManagementApplicationService userManagementApplicationService,
            CurrentUserAccessor currentUserAccessor) {
        this.userManagementApplicationService = userManagementApplicationService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public PagedResponse<UserSummaryResponse> list(
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) @Min(1) int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) @Min(1) @Max(MAX_SIZE) int size,
            @RequestParam(required = false) @Pattern(regexp = "LOCAL|DINGTALK", message = "provider 必须是 LOCAL 或 DINGTALK")
                    String provider) {
        IdentityProvider providerFilter = provider == null ? null : IdentityProvider.valueOf(provider);
        PageResult<UserWithIdentities> result =
                userManagementApplicationService.listUsers(page, size, providerFilter);
        return PagedResponse.from(result, UserSummaryResponse::from);
    }

    @PostMapping
    public UserSummaryResponse create(@Valid @RequestBody CreateUserRequest request) {
        AppUser created = userManagementApplicationService.createUser(
                request.displayName(), request.login(), request.password(), request.role());
        return UserSummaryResponse.from(new UserWithIdentities(created, List.of()));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable long id) {
        AuthenticatedUser operator = currentUserAccessor.require();
        userManagementApplicationService.disableUser(id, operator.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable long id) {
        userManagementApplicationService.enableUser(id);
        return ResponseEntity.noContent().build();
    }
}
