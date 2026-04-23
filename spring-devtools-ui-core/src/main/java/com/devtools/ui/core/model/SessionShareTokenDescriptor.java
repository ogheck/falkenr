package com.devtools.ui.core.model;

public record SessionShareTokenDescriptor(
        String role,
        String tokenPreview,
        String shareUrl,
        String expiresAt,
        boolean active
) {
}
