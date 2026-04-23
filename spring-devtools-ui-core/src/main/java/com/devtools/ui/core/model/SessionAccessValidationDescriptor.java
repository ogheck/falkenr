package com.devtools.ui.core.model;

public record SessionAccessValidationDescriptor(
        boolean allowed,
        String role,
        String reason,
        int viewerCount,
        String sessionId
) {
}
