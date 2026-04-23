package com.devtools.ui.core.sanitize;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataSanitizerTest {

    @Test
    void sanitizesJsonPayloadFieldsAndTestEmails() {
        String sanitized = SensitiveDataSanitizer.sanitizePayload(
                "{\"email\":\"demo.account@example.com\",\"password\":\"super-secret\",\"owner\":\"real.user@example.com\"}"
        );

        assertThat(sanitized).contains("\"email\":\"[masked]\"");
        assertThat(sanitized).contains("\"password\":\"[masked]\"");
        assertThat(sanitized).contains("real.user@example.com");
    }

    @Test
    void sanitizesFormPayloadFieldsAndQaEmails() {
        String sanitized = SensitiveDataSanitizer.sanitizePayload(
                "email=qa.user@example.com&token=abc123&owner=real.user@example.com"
        );

        assertThat(sanitized).contains("email=[masked]");
        assertThat(sanitized).contains("token=[masked]");
        assertThat(sanitized).contains("owner=real.user@example.com");
    }
}
