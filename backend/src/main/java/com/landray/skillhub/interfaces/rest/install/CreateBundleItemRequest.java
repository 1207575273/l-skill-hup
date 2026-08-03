package com.landray.skillhub.interfaces.rest.install;

import jakarta.validation.constraints.NotNull;

/** {@code POST /bundles} 请求体里 {@code items} 数组的一个元素。 */
public record CreateBundleItemRequest(
        @NotNull(message = "skillId 不能为空") Long skillId, @NotNull(message = "versionId 不能为空") Long versionId) {}
