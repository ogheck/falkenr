package com.devtools.ui.autoconfigure;

public record RelayAccessTokenRequest(
        String sessionId,
        String token,
        String role,
        String expiresAt
) {
}
