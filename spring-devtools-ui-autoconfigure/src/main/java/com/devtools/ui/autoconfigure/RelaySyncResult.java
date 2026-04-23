package com.devtools.ui.autoconfigure;

public record RelaySyncResult(
        String syncId,
        String viewId,
        String relayStatus,
        boolean accepted
) {
}
