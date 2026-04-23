package com.devtools.ui.core.model;

import java.util.List;

public record FakeExternalServiceDescriptor(
        String serviceId,
        String displayName,
        String description,
        String basePath,
        boolean enabled,
        List<String> routes,
        List<FakeExternalServiceMockDescriptor> mockResponses
) {
}
