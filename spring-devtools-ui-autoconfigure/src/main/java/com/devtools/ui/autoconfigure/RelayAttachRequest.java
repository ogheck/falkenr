package com.devtools.ui.autoconfigure;

import java.util.List;

record RelayAttachRequest(
        String sessionId,
        String ownerName,
        List<String> allowedRoles,
        String relayUrl,
        String shareUrl,
        String encryptedToken,
        String expiresAt
) {
}
