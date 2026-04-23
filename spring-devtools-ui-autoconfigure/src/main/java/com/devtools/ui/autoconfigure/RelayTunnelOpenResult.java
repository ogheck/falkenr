package com.devtools.ui.autoconfigure;

public record RelayTunnelOpenResult(
        String tunnelId,
        String tunnelStatus,
        String openedAt,
        String streamUrl
) {
}
