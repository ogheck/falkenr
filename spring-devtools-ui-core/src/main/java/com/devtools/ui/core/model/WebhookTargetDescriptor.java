package com.devtools.ui.core.model;

public record WebhookTargetDescriptor(
        String method,
        String path,
        String controller,
        String methodName
) {
}
