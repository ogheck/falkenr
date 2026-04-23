package com.devtools.ui.autoconfigure;

record ErrorReplayResult(
        String requestId,
        String method,
        String path,
        int originalStatus,
        int replayStatus,
        String replayedAt,
        String responseBody
) {
}
