package com.devtools.ui.core.model;

public record SessionReplayEntryDescriptor(
        String replayId,
        String category,
        String title,
        String payloadPreview,
        String artifactType,
        String artifactId,
        String occurredAt
) {
}
