package com.devtools.ui.autoconfigure;

record FakeExternalServiceMockUpdateRequest(
        String serviceId,
        String routeId,
        int status,
        String contentType,
        String body
) {
}
