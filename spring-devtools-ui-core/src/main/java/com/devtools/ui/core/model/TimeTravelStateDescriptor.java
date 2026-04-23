package com.devtools.ui.core.model;

public record TimeTravelStateDescriptor(
        String currentTime,
        String zoneId,
        boolean overridden,
        String overrideReason,
        String overriddenBy,
        String overriddenAt,
        String expiresAt
) {
}
