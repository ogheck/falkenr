package com.devtools.ui.autoconfigure;

public record SessionInspectRequest(
        String artifactType,
        String artifactId,
        String actor
) {
}
