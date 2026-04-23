package com.devtools.ui.core.config;

import com.devtools.ui.core.model.ConfigPropertyDescriptor;
import com.devtools.ui.core.policy.DevToolsDataPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigInspectorTest {

    private static final DevToolsDataPolicy DEFAULT_POLICY = new DevToolsDataPolicy() {
        @Override public boolean shouldCaptureRequest(String path) { return true; }
        @Override public boolean captureRequestHeaders() { return true; }
        @Override public boolean captureRequestBodies() { return true; }
        @Override public boolean truncateRequestBodies() { return true; }
        @Override public String sanitizeConfigValue(String key, String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeConfigValue(key, value); }
        @Override public java.util.Map<String, java.util.List<String>> sanitizeHeaders(java.util.Map<String, java.util.List<String>> headers) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeHeaders(headers); }
        @Override public String sanitizeSql(String sql) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeSql(sql); }
        @Override public String sanitizeScheduledValue(String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeScheduledValue(value); }
        @Override public String sanitizePayload(String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizePayload(value); }
        @Override public boolean maskSessionSecrets() { return true; }
        @Override public String sanitizeSessionSecret(String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeSessionSecret(value); }
        @Override public String maskValue() { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.MASKED_VALUE; }
    };

    @Test
    void inspectReturnsResolvedValueAndFormattedPropertySourceName() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource(
                "Config resource 'class path resource [application.yml]' via location 'optional:classpath:/'"
        ).withProperty("server.port", "8080"));

        ConfigInspector inspector = new ConfigInspector(environment, DEFAULT_POLICY);

        assertThat(inspector.inspect())
                .contains(new ConfigPropertyDescriptor("server.port", "8080", "application.yml"));
    }

    @Test
    void inspectMasksSensitiveConfigValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource("test-source")
                .withProperty("spring.datasource.password", "super-secret"));

        ConfigInspector inspector = new ConfigInspector(environment, DEFAULT_POLICY);

        assertThat(inspector.inspect())
                .contains(new ConfigPropertyDescriptor("spring.datasource.password", "[masked]", "test-source"));
    }
}
