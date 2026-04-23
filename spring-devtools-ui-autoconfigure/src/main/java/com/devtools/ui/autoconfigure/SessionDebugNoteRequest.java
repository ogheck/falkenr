package com.devtools.ui.autoconfigure;

public record SessionDebugNoteRequest(
        String author,
        String message,
        String artifactType,
        String artifactId
) {
}
