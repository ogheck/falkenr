package com.devtools.ui.autoconfigure;

public record RelayTunnelCloseRequest(
        String sessionId,
        String connectionId,
        String tunnelId
) {
}
