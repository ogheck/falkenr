package com.devtools.ui.autoconfigure;

public record RelayTunnelOpenRequest(
        String sessionId,
        String connectionId
) {
}
