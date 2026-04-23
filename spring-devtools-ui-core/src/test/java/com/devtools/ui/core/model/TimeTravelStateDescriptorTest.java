package com.devtools.ui.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeTravelStateDescriptorTest {

    @Test
    void recordExposesClockOverrideState() {
        TimeTravelStateDescriptor descriptor = new TimeTravelStateDescriptor(
                "2026-04-08T16:00:00Z",
                "UTC",
                true,
                "staging verification",
                "alice@example.com",
                "2026-04-08T15:55:00Z",
                "2026-04-08T16:25:00Z"
        );

        assertThat(descriptor.currentTime()).isEqualTo("2026-04-08T16:00:00Z");
        assertThat(descriptor.zoneId()).isEqualTo("UTC");
        assertThat(descriptor.overridden()).isTrue();
        assertThat(descriptor.overrideReason()).isEqualTo("staging verification");
        assertThat(descriptor.overriddenBy()).isEqualTo("alice@example.com");
        assertThat(descriptor.overriddenAt()).isEqualTo("2026-04-08T15:55:00Z");
        assertThat(descriptor.expiresAt()).isEqualTo("2026-04-08T16:25:00Z");
    }
}
