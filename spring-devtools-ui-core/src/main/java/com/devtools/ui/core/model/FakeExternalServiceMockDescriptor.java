package com.devtools.ui.core.model;

public record FakeExternalServiceMockDescriptor(
        String routeId,
        String route,
        int status,
        String contentType,
        String body
) {
}
