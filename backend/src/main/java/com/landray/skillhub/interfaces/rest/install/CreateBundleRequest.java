package com.landray.skillhub.interfaces.rest.install;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** {@code POST /bundles} 请求体。 */
public record CreateBundleRequest(
        @NotEmpty(message = "items 不能为空") @Valid List<CreateBundleItemRequest> items,
        @NotBlank(message = "client 不能为空") String client,
        @NotBlank(message = "scope 不能为空") String scope) {}
