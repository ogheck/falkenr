package com.devtools.ui.relay;

import com.devtools.ui.core.model.RelayAccountDescriptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/sessions/billing")
class RelayBillingController {

    private final InMemoryRelaySessionStore sessionStore;
    private final RelayServerProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    RelayBillingController(InMemoryRelaySessionStore sessionStore,
                           RelayServerProperties properties,
                           ObjectMapper objectMapper) {
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @PostMapping("/checkout")
    RelayBillingCheckoutResponse createCheckout(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                HttpServletRequest httpRequest,
                                                @RequestBody(required = false) RelayBillingCheckoutRequest request) throws Exception {
        requireStripeCheckoutConfigured();
        RelayAccountDescriptor owner = sessionStore.billingOwner(resolveAccountSessionToken(accountSessionToken, httpRequest));
        int seatQuantity = normalizeSeatQuantity(request == null ? null : request.seatQuantity());

        String form = formBody(List.of(
                entry("mode", "subscription"),
                entry("success_url", properties.billingSuccessUrl()),
                entry("cancel_url", properties.billingCancelUrl()),
                entry("client_reference_id", owner.organizationId()),
                entry("line_items[0][price]", properties.stripeTeamPriceId()),
                entry("line_items[0][quantity]", String.valueOf(seatQuantity)),
                entry("metadata[organizationId]", owner.organizationId()),
                entry("metadata[accountId]", owner.accountId()),
                entry("metadata[plan]", "team"),
                entry("subscription_data[metadata][organizationId]", owner.organizationId()),
                entry("subscription_data[metadata][accountId]", owner.accountId()),
                entry("subscription_data[metadata][plan]", "team")
        ));

        HttpRequest stripeRequest = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                .header("Authorization", "Bearer " + properties.stripeApiKey())
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(stripeRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Stripe checkout session creation failed");
        }

        JsonNode json = objectMapper.readTree(response.body());
        return new RelayBillingCheckoutResponse(
                "stripe",
                json.path("id").asText(),
                json.path("url").asText(),
                owner.organizationId(),
                seatQuantity
        );
    }

    @PostMapping("/portal")
    RelayBillingPortalResponse createPortalSession(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                   HttpServletRequest httpRequest) throws Exception {
        requireStripeApiConfigured();
        String resolvedAccountSession = resolveAccountSessionToken(accountSessionToken, httpRequest);
        RelayAccountDescriptor owner = sessionStore.billingOwner(resolvedAccountSession);
        String stripeCustomerId = sessionStore.billingCustomerId(resolvedAccountSession);

        String form = formBody(List.of(
                entry("customer", stripeCustomerId),
                entry("return_url", properties.publicBaseUrl() + "/app?billing=portal")
        ));

        HttpRequest stripeRequest = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/billing_portal/sessions"))
                .header("Authorization", "Bearer " + properties.stripeApiKey())
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(stripeRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Stripe customer portal session creation failed");
        }

        JsonNode json = objectMapper.readTree(response.body());
        return new RelayBillingPortalResponse(
                "stripe",
                json.path("id").asText(),
                json.path("url").asText(),
                owner.organizationId()
        );
    }

    @PostMapping("/stripe/webhook")
    RelayBillingWebhookResponse stripeWebhook(@RequestBody String body,
                                              @RequestHeader(name = "Stripe-Signature", required = false) String signature) throws Exception {
        requireStripeWebhookConfigured();
        verifyStripeSignature(body, signature);

        JsonNode event = objectMapper.readTree(body);
        String type = event.path("type").asText();
        JsonNode object = event.path("data").path("object");
        String organizationId = organizationIdFrom(object);
        if (organizationId.isBlank()) {
            return new RelayBillingWebhookResponse(true, type, "", "ignored");
        }

        if ("checkout.session.completed".equals(type)
                || "customer.subscription.created".equals(type)
                || "customer.subscription.updated".equals(type)) {
            int seats = Math.max(1, object.path("quantity").asInt(1));
            sessionStore.billingUpdateStripeLink(
                    organizationId,
                    customerIdFrom(object),
                    subscriptionIdFrom(type, object)
            );
            RelayEntitlementResponse entitlement = sessionStore.billingUpdateEntitlement(
                    organizationId,
                    new RelayEntitlementRequest("team", "active", seats)
            );
            return new RelayBillingWebhookResponse(true, type, organizationId, entitlement.status());
        }

        if ("customer.subscription.deleted".equals(type)) {
            RelayEntitlementResponse entitlement = sessionStore.billingUpdateEntitlement(
                    organizationId,
                    new RelayEntitlementRequest("team", "canceled", 0)
            );
            return new RelayBillingWebhookResponse(true, type, organizationId, entitlement.status());
        }

        return new RelayBillingWebhookResponse(true, type, organizationId, "ignored");
    }

    private String resolveAccountSessionToken(String accountSessionToken, HttpServletRequest request) {
        if (accountSessionToken != null && !accountSessionToken.isBlank()) {
            return accountSessionToken.trim();
        }
        if (request == null || request.getSession(false) == null) {
            throw new RelayAuthException("Missing relay account session");
        }
        Object sessionToken = request.getSession(false).getAttribute(RelayWebLoginSuccessHandler.ACCOUNT_SESSION_ATTR);
        if (sessionToken instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new RelayAuthException("Missing relay account session");
    }

    private void requireStripeCheckoutConfigured() {
        if (properties.stripeApiKey().isBlank() || properties.stripeTeamPriceId().isBlank()) {
            throw new BillingNotConfiguredException("Stripe checkout requires stripe-api-key and stripe-team-price-id");
        }
    }

    private void requireStripeApiConfigured() {
        if (properties.stripeApiKey().isBlank()) {
            throw new BillingNotConfiguredException("Stripe billing requires stripe-api-key");
        }
    }

    private void requireStripeWebhookConfigured() {
        if (properties.stripeWebhookSecret().isBlank()) {
            throw new BillingNotConfiguredException("Stripe webhook verification requires stripe-webhook-secret");
        }
    }

    private int normalizeSeatQuantity(Integer requested) {
        if (requested == null || requested <= 0) {
            return 1;
        }
        return Math.min(requested, 250);
    }

    private String organizationIdFrom(JsonNode object) {
        String fromMetadata = object.path("metadata").path("organizationId").asText("");
        if (!fromMetadata.isBlank()) {
            return fromMetadata.trim();
        }
        return object.path("client_reference_id").asText("").trim();
    }

    private String customerIdFrom(JsonNode object) {
        JsonNode customer = object.path("customer");
        if (customer.isTextual()) {
            return customer.asText("").trim();
        }
        return customer.path("id").asText("").trim();
    }

    private String subscriptionIdFrom(String eventType, JsonNode object) {
        if (eventType != null && eventType.startsWith("customer.subscription.")) {
            return object.path("id").asText("").trim();
        }
        JsonNode subscription = object.path("subscription");
        if (subscription.isTextual()) {
            return subscription.asText("").trim();
        }
        return subscription.path("id").asText("").trim();
    }

    private void verifyStripeSignature(String body, String signature) throws Exception {
        if (signature == null || signature.isBlank()) {
            throw new RelayAuthException("Missing Stripe signature");
        }

        String timestamp = "";
        List<String> signatures = new ArrayList<>();
        for (String part : signature.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                timestamp = pair[1];
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }

        if (timestamp.isBlank() || signatures.isEmpty()) {
            throw new RelayAuthException("Invalid Stripe signature");
        }
        long signedAt = Long.parseLong(timestamp);
        long ageSeconds = Math.abs(Instant.now().getEpochSecond() - signedAt);
        if (ageSeconds > 300) {
            throw new RelayAuthException("Expired Stripe signature");
        }

        String expected = hmacSha256Hex(properties.stripeWebhookSecret(), timestamp + "." + body);
        boolean matched = signatures.stream().anyMatch(actual -> MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        ));
        if (!matched) {
            throw new RelayAuthException("Invalid Stripe signature");
        }
    }

    private String hmacSha256Hex(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private FormEntry entry(String name, String value) {
        return new FormEntry(name, value == null ? "" : value);
    }

    private String formBody(List<FormEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.value().isBlank())
                .map(entry -> encode(entry.name()) + "=" + encode(entry.value()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record FormEntry(String name, String value) {
    }
}
