package com.devtools.ui.relay;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "spring.devtools.ui.relay-server")
public record RelayServerProperties(
        String publicBaseUrl,
        Duration leaseTtl,
        int hostedHistoryLimit,
        String persistenceFile,
        String authIssuer,
        String authSecret,
        String stripeApiKey,
        String stripeTeamPriceId,
        String stripeWebhookSecret,
        String billingSuccessUrl,
        String billingCancelUrl
) {

    public RelayServerProperties {
        publicBaseUrl = normalize(publicBaseUrl);
        leaseTtl = leaseTtl == null ? Duration.ofMinutes(10) : leaseTtl;
        hostedHistoryLimit = hostedHistoryLimit <= 0 ? 50 : hostedHistoryLimit;
        persistenceFile = normalizePersistenceFile(persistenceFile);
        authIssuer = normalizeIssuer(authIssuer);
        authSecret = normalizeSecret(authSecret);
        stripeApiKey = normalizeBlank(stripeApiKey);
        stripeTeamPriceId = normalizeBlank(stripeTeamPriceId);
        stripeWebhookSecret = normalizeBlank(stripeWebhookSecret);
        billingSuccessUrl = normalizeBillingUrl(billingSuccessUrl, publicBaseUrl + "/app?billing=success");
        billingCancelUrl = normalizeBillingUrl(billingCancelUrl, publicBaseUrl + "/pricing?billing=cancelled");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8091";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String normalizePersistenceFile(String value) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return System.getProperty("user.home") + "/.spring-devtools-ui/relay-server-state.json";
    }

    private static String normalizeIssuer(String value) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return "spring-devtools-ui-relay";
    }

    private static String normalizeSecret(String value) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        // Dev default only. Paid/shared deployments must supply a real secret.
        return "dev-insecure-secret-change-me";
    }

    private static String normalizeBlank(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String normalizeBillingUrl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
