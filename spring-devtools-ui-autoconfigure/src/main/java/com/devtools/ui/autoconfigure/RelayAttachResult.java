package com.devtools.ui.autoconfigure;

record RelayAttachResult(
        String handshakeId,
        String connectionId,
        String relayStatus,
        String leaseId,
        String leaseExpiresAt,
        String viewerUrl,
        String organizationId,
        String organizationName,
        String ownerAccountId
) {
}
