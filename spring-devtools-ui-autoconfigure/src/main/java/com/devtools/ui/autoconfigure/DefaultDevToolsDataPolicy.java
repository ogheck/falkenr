package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.policy.DevToolsDataPolicy;
import com.devtools.ui.core.sanitize.SensitiveDataSanitizer;

import java.util.List;
import java.util.Map;

final class DefaultDevToolsDataPolicy implements DevToolsDataPolicy {

    private final DevToolsUiProperties.PolicySettings settings;

    DefaultDevToolsDataPolicy(DevToolsUiProperties.PolicySettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean shouldCaptureRequest(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        return settings.getExcludedPaths().stream().noneMatch(pattern -> matches(pattern, path));
    }

    @Override
    public boolean captureRequestHeaders() {
        return settings.isCaptureRequestHeaders();
    }

    @Override
    public boolean captureRequestBodies() {
        return settings.isCaptureRequestBodies();
    }

    @Override
    public boolean truncateRequestBodies() {
        return settings.isTruncateRequestBodies();
    }

    @Override
    public String sanitizeConfigValue(String key, String value) {
        return settings.isMaskConfigValues()
                ? SensitiveDataSanitizer.sanitizeConfigValue(key, value)
                : value == null ? "" : value;
    }

    @Override
    public Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        if (!settings.isCaptureRequestHeaders()) {
            return Map.of();
        }
        return settings.isMaskRequestHeaders() ? SensitiveDataSanitizer.sanitizeHeaders(headers) : headers;
    }

    @Override
    public String sanitizeSql(String sql) {
        return settings.isMaskSql() ? SensitiveDataSanitizer.sanitizeSql(sql) : (sql == null ? "" : sql);
    }

    @Override
    public String sanitizeScheduledValue(String value) {
        return settings.isMaskScheduledValues() ? SensitiveDataSanitizer.sanitizeScheduledValue(value) : (value == null ? "" : value);
    }

    @Override
    public String sanitizePayload(String value) {
        return settings.isMaskPayloads() ? SensitiveDataSanitizer.sanitizePayload(value) : (value == null ? "" : value);
    }

    @Override
    public boolean maskSessionSecrets() {
        return settings.isMaskSessionSecrets();
    }

    @Override
    public String sanitizeSessionSecret(String value) {
        return settings.isMaskSessionSecrets() ? SensitiveDataSanitizer.sanitizeSessionSecret(value) : value;
    }

    @Override
    public String maskValue() {
        return SensitiveDataSanitizer.MASKED_VALUE;
    }

    private boolean matches(String pattern, String path) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String normalizedPattern = pattern.trim();
        if (normalizedPattern.endsWith("/**")) {
            return path.startsWith(normalizedPattern.substring(0, normalizedPattern.length() - 3));
        }
        return path.equals(normalizedPattern);
    }
}
