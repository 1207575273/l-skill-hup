package com.landray.skillhub.application.upload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.model.UploadLimits;

class UploadPropertiesTest {

    @Test
    void should_convertToUploadLimits_when_toLimitsCalled() {
        UploadProperties properties = new UploadProperties(67_108_864L, 500, 16_777_216L, 8, 255, 100);

        UploadLimits limits = properties.toLimits();

        assertThat(limits)
                .isEqualTo(new UploadLimits(67_108_864L, 500, 16_777_216L, 8, 255, 100));
    }
}
