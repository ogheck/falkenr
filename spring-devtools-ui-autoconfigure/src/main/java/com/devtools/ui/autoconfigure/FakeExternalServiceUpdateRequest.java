package com.devtools.ui.autoconfigure;

record FakeExternalServiceUpdateRequest(
        String serviceId,
        boolean enabled,
        FakeExternalServiceMockUpdateRequest mockResponse
) {
}
