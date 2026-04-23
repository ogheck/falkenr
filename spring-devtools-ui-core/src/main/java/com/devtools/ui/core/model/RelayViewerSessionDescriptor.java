package com.devtools.ui.core.model;

public record RelayViewerSessionDescriptor(
        String viewerSessionId,
        String role,
        String viewerName,
        String createdAt,
        String expiresAt
) {
}
