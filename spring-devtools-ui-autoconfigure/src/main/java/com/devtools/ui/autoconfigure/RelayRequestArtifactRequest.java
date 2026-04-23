package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.CapturedRequest;

public record RelayRequestArtifactRequest(
        String sessionId,
        CapturedRequest request
) {
}
