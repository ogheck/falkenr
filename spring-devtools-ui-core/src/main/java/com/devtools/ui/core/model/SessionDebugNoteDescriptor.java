package com.devtools.ui.core.model;

public record SessionDebugNoteDescriptor(
        String noteId,
        String author,
        String message,
        String artifactType,
        String artifactId,
        String createdAt
) {
}
