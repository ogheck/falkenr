package com.devtools.ui.core.model;

public record EndpointDescriptor(
        String method,
        String path,
        String controller,
        String methodName
) {
}
