package com.devtools.ui.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CapturedRequest(
        String requestId,
        String method,
        String path,
        Map<String, List<String>> headers,
        String body,
        boolean bodyTruncated,
        boolean binaryBody,
        Instant timestamp,
        int responseStatus
) {
}
