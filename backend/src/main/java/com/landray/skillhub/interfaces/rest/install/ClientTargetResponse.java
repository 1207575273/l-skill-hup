package com.landray.skillhub.interfaces.rest.install;

import com.landray.skillhub.domain.model.ClientTarget;

/** {@code GET /clients} 的一个可选项。 */
public record ClientTargetResponse(String client, String scope, String skillsSubDir) {

    public static ClientTargetResponse from(ClientTarget target) {
        return new ClientTargetResponse(
                target.client().apiCode(), target.scope().apiCode(), target.client().skillsSubDir());
    }
}
