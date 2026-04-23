package com.devtools.ui.core.fakes;

public record FakeExternalServiceMockResponse(
        int status,
        String contentType,
        String body
) {
}
