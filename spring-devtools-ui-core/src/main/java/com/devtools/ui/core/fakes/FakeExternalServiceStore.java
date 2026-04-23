package com.devtools.ui.core.fakes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeExternalServiceStore {

    private final ConcurrentHashMap<String, Boolean> enabledByService = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FakeExternalServiceMockResponse> mocksByRouteId = new ConcurrentHashMap<>();

    public void setEnabled(String serviceId, boolean enabled) {
        enabledByService.put(serviceId, enabled);
    }

    public boolean isEnabled(String serviceId) {
        return Boolean.TRUE.equals(enabledByService.get(serviceId));
    }

    public void setMockResponse(String routeId, FakeExternalServiceMockResponse response) {
        mocksByRouteId.put(routeId, response);
    }

    public FakeExternalServiceMockResponse mockResponse(String routeId) {
        return mocksByRouteId.get(routeId);
    }

    public Map<String, Boolean> snapshot() {
        return Map.copyOf(enabledByService);
    }

    public Map<String, FakeExternalServiceMockResponse> mockSnapshot() {
        return Map.copyOf(mocksByRouteId);
    }
}
