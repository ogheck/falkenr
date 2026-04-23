package com.devtools.ui.autoconfigure;

record RelayHeartbeatResult(
        String relayStatus,
        String tunnelStatus,
        boolean connected,
        String leaseId,
        String leaseExpiresAt
) {
}
