package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.RemoteSessionDescriptor;

public record RelaySyncRequest(
        String sessionId,
        String connectionId,
        RemoteSessionDescriptor snapshot
) {
}
