package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.fakes.FakeExternalServiceStore;
import com.devtools.ui.core.fakes.FakeExternalServiceMockResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(DevToolsUiConstants.ROOT_PATH + "/fake")
class DevToolsFakeExternalServicesController {

    private final DevToolsUiProperties properties;
    private final FakeExternalServiceStore store;

    DevToolsFakeExternalServicesController(DevToolsUiProperties properties, FakeExternalServiceStore store) {
        this.properties = properties;
        this.store = store;
    }

    @GetMapping("/github/status")
    ResponseEntity<?> githubStatus() {
        if (!enabled("github")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "github fake service disabled"));
        }
        FakeExternalServiceMockResponse mock = store.mockResponse("github:GET /_dev/fake/github/status");
        if (mock != null) {
            return responseFromMock(mock);
        }
        return ResponseEntity.ok(Map.of("service", "github", "status", "ready"));
    }

    @PostMapping(path = "/github/webhooks", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> githubWebhook(@RequestBody Map<String, Object> payload) {
        if (!enabled("github")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "github fake service disabled"));
        }
        FakeExternalServiceMockResponse mock = store.mockResponse("github:POST /_dev/fake/github/webhooks");
        if (mock != null) {
            return responseFromMock(mock);
        }
        return ResponseEntity.ok(Map.of("accepted", true, "service", "github", "payload", payload));
    }

    @GetMapping("/stripe/customers/{customerId}")
    ResponseEntity<?> stripeCustomer(@PathVariable String customerId) {
        if (!enabled("stripe")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "stripe fake service disabled"));
        }
        FakeExternalServiceMockResponse mock = store.mockResponse("stripe:GET /_dev/fake/stripe/customers/{customerId}");
        if (mock != null) {
            return responseFromMock(mock);
        }
        return ResponseEntity.ok(Map.of(
                "id", customerId,
                "object", "customer",
                "email", "demo@example.com",
                "name", "DevTools Demo Customer"
        ));
    }

    @PostMapping(path = "/stripe/customers", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> createStripeCustomer(@RequestBody Map<String, Object> payload) {
        if (!enabled("stripe")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "stripe fake service disabled"));
        }
        FakeExternalServiceMockResponse mock = store.mockResponse("stripe:POST /_dev/fake/stripe/customers");
        if (mock != null) {
            return responseFromMock(mock);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", "cus_demo",
                "object", "customer",
                "created", true,
                "request", payload
        ));
    }

    private boolean enabled(String serviceId) {
        return properties.getFeatures().isFakeServices() && store.isEnabled(serviceId);
    }

    private ResponseEntity<String> responseFromMock(FakeExternalServiceMockResponse mock) {
        MediaType mediaType = MediaType.parseMediaType(
                mock.contentType() == null || mock.contentType().isBlank() ? MediaType.APPLICATION_JSON_VALUE : mock.contentType()
        );
        return ResponseEntity.status(mock.status())
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .body(mock.body() == null ? "" : mock.body());
    }
}
