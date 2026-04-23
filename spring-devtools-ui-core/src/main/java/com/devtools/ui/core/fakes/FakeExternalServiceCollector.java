package com.devtools.ui.core.fakes;

import com.devtools.ui.core.collector.DevToolsCollector;
import com.devtools.ui.core.model.FakeExternalServiceDescriptor;
import com.devtools.ui.core.model.FakeExternalServiceMockDescriptor;

import java.util.List;

public class FakeExternalServiceCollector implements DevToolsCollector<FakeExternalServiceDescriptor> {

    private static final List<FakeExternalServiceDescriptor> SERVICE_BLUEPRINTS = List.of(
            new FakeExternalServiceDescriptor(
                    "github",
                    "GitHub Webhooks",
                    "Responds like a simple webhook receiver and delivery probe target.",
                    "/_dev/fake/github",
                    false,
                    List.of("POST /_dev/fake/github/webhooks", "GET /_dev/fake/github/status"),
                    List.of()
            ),
            new FakeExternalServiceDescriptor(
                    "stripe",
                    "Stripe Customers",
                    "Returns canned customer objects for local payment and billing flows.",
                    "/_dev/fake/stripe",
                    false,
                    List.of("POST /_dev/fake/stripe/customers", "GET /_dev/fake/stripe/customers/{customerId}"),
                    List.of()
            )
    );

    private final FakeExternalServiceStore store;

    public FakeExternalServiceCollector(FakeExternalServiceStore store) {
        this.store = store;
    }

    @Override
    public String id() {
        return "fakeExternalServices";
    }

    @Override
    public List<FakeExternalServiceDescriptor> collect() {
        return SERVICE_BLUEPRINTS.stream()
                .map(service -> new FakeExternalServiceDescriptor(
                        service.serviceId(),
                        service.displayName(),
                        service.description(),
                        service.basePath(),
                        store.isEnabled(service.serviceId()),
                        service.routes(),
                        service.routes().stream()
                                .map(route -> new FakeExternalServiceMockDescriptor(
                                        routeId(service.serviceId(), route),
                                        route,
                                        mockStatus(service.serviceId(), route),
                                        mockContentType(service.serviceId(), route),
                                        mockBody(service.serviceId(), route)
                                ))
                                .toList()
                ))
                .toList();
    }

    private int mockStatus(String serviceId, String route) {
        FakeExternalServiceMockResponse response = store.mockResponse(routeId(serviceId, route));
        return response == null ? 200 : response.status();
    }

    private String mockContentType(String serviceId, String route) {
        FakeExternalServiceMockResponse response = store.mockResponse(routeId(serviceId, route));
        return response == null ? "application/json" : response.contentType();
    }

    private String mockBody(String serviceId, String route) {
        FakeExternalServiceMockResponse response = store.mockResponse(routeId(serviceId, route));
        return response == null ? "" : response.body();
    }

    public static String routeId(String serviceId, String route) {
        return serviceId + ":" + route;
    }
}
