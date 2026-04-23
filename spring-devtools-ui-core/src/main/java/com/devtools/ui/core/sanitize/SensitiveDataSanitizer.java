package com.devtools.ui.core.sanitize;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SensitiveDataSanitizer {

    public static final String MASKED_VALUE = "[masked]";

    private static final List<String> SENSITIVE_CONFIG_TOKENS = List.of(
            "password",
            "secret",
            "token",
            "credential",
            "private-key",
            "private_key",
            "access-key",
            "access_key",
            "api-key",
            "api_key",
            "auth"
    );

    private static final List<String> SENSITIVE_HEADER_NAMES = List.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token",
            "x-csrf-token",
            "x-forwarded-access-token"
    );

    private static final List<String> SENSITIVE_HEADER_TOKENS = List.of(
            "auth",
            "token",
            "secret",
            "cookie",
            "session",
            "jwt",
            "apikey",
            "accesskey",
            "privatekey"
    );

    private static final Pattern SINGLE_QUOTED_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern DOUBLE_QUOTED_LITERAL = Pattern.compile("\"(?:\"\"|[^\"])*\"");
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("(?<![\\w.])-?\\d{4,}(?![\\w.])");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)("
                    + "password|passwd|secret|token|credential|api[_-]?key|access[_-]?key|private[_-]?key"
                    + "\\s*=\\s*)"
                    + "([^\\s,&)]+)"
    );
    private static final Pattern JSON_SENSITIVE_FIELD = Pattern.compile(
            "(?i)(\"(?:password|passwd|secret|token|credential|api[_-]?key|access[_-]?key|private[_-]?key|session[_-]?id|authorization)\"\\s*:\\s*)\"([^\"]*)\""
    );
    private static final Pattern FORM_SENSITIVE_FIELD = Pattern.compile(
            "(?i)(^|[&\\s])((?:password|passwd|secret|token|credential|api[_-]?key|access[_-]?key|private[_-]?key|session[_-]?id|authorization)=)([^&\\s]+)"
    );
    private static final Pattern PLAIN_SENSITIVE_FIELD = Pattern.compile(
            "(?i)\\b(password|passwd|secret|token|credential|api[_-]?key|access[_-]?key|private[_-]?key|session[_-]?id|authorization)(\\s*[:=]\\s*)([^\\s,&)]+)"
    );
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "(?i)\\b([a-z0-9._%+-]+)@([a-z0-9.-]+\\.[a-z]{2,})\\b"
    );
    private static final Pattern URL_SECRET_PARAM = Pattern.compile(
            "(?i)([?&](?:token|access_token|accountSession|viewerSession|sessionToken)=)([^&#]+)"
    );
    private static final Pattern TOKENISH_VALUE = Pattern.compile(
            "(?i)\\b(?:share_[a-z0-9-]+|invite_token_[a-z0-9-]+|acctsess_[a-z0-9-]+|viewersess_[a-z0-9-]+)\\b"
    );
    private static final Pattern SCHEDULE_PROPERTY_PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");

    private SensitiveDataSanitizer() {
    }

    public static String sanitizeConfigValue(String key, String value) {
        if (value == null) {
            return "";
        }
        return isSensitiveConfigKey(key) ? MASKED_VALUE : value;
    }

    public static Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> isSensitiveHeader(entry.getKey())
                                ? List.of(MASKED_VALUE)
                                : entry.getValue(),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    public static boolean isSensitiveConfigKey(String key) {
        String normalizedKey = normalize(key);
        return SENSITIVE_CONFIG_TOKENS.stream().anyMatch(normalizedKey::contains);
    }

    public static boolean isSensitiveHeader(String headerName) {
        String normalizedHeaderName = normalize(headerName);
        String compactHeaderName = compact(headerName);
        return SENSITIVE_HEADER_NAMES.stream().anyMatch(normalizedHeaderName::equals)
                || SENSITIVE_HEADER_TOKENS.stream().anyMatch(compactHeaderName::contains);
    }

    public static String sanitizeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }

        String sanitized = sql;
        sanitized = SINGLE_QUOTED_LITERAL.matcher(sanitized).replaceAll("'" + MASKED_VALUE + "'");
        sanitized = DOUBLE_QUOTED_LITERAL.matcher(sanitized).replaceAll("\"" + MASKED_VALUE + "\"");
        sanitized = NUMERIC_LITERAL.matcher(sanitized).replaceAll(MASKED_VALUE);
        return sanitized;
    }

    public static String sanitizeScheduledValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        java.util.regex.Matcher matcher = SCHEDULE_PROPERTY_PLACEHOLDER.matcher(value);
        StringBuffer buffer = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String placeholderKey = matcher.group(1);
            String replacement = isSensitiveConfigKey(placeholderKey)
                    ? "${" + placeholderKey + ":" + MASKED_VALUE + "}"
                    : matcher.group(0);
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        if (found) {
            matcher.appendTail(buffer);
            return buffer.toString();
        }

        if (isSensitiveConfigKey(value)) {
            return MASKED_VALUE;
        }

        return replaceSensitiveAssignments(value);
    }

    public static String sanitizePayload(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String sanitized = replaceJsonSensitiveFields(value);
        sanitized = replaceFormSensitiveFields(sanitized);
        sanitized = replacePlainSensitiveFields(sanitized);
        sanitized = replaceTestAccountEmails(sanitized);
        return sanitized;
    }

    public static String sanitizeSessionSecret(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? null : "";
        }
        String sanitized = URL_SECRET_PARAM.matcher(value).replaceAll("$1" + MASKED_VALUE);
        sanitized = TOKENISH_VALUE.matcher(sanitized).replaceAll(MASKED_VALUE);
        return sanitized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String compact(String value) {
        return normalize(value).replaceAll("[^a-z0-9]", "");
    }

    private static String replaceSensitiveAssignments(String value) {
        java.util.regex.Matcher matcher = SENSITIVE_ASSIGNMENT.matcher(value);
        StringBuffer buffer = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String key = normalize(matcher.group(1));
            String replacement = isSensitiveConfigKey(key)
                    ? matcher.group(1) + MASKED_VALUE
                    : matcher.group(0);
            matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        if (!found) {
            return value;
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceJsonSensitiveFields(String value) {
        java.util.regex.Matcher matcher = JSON_SENSITIVE_FIELD.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    buffer,
                    java.util.regex.Matcher.quoteReplacement(matcher.group(1) + "\"" + MASKED_VALUE + "\"")
            );
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceFormSensitiveFields(String value) {
        java.util.regex.Matcher matcher = FORM_SENSITIVE_FIELD.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    buffer,
                    java.util.regex.Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + MASKED_VALUE)
            );
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceTestAccountEmails(String value) {
        java.util.regex.Matcher matcher = EMAIL_ADDRESS.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String email = matcher.group(0);
            String localPart = matcher.group(1);
            String normalizedLocalPart = normalize(localPart);
            if (normalizedLocalPart.contains("test")
                    || normalizedLocalPart.contains("qa")
                    || normalizedLocalPart.contains("demo")
                    || normalizedLocalPart.contains("staging")
                    || normalizedLocalPart.contains("fake")
                    || normalizedLocalPart.endsWith("+test")
                    || normalizedLocalPart.endsWith("+qa")
                    || normalizedLocalPart.endsWith("+demo")
                    || normalizedLocalPart.endsWith("+staging")) {
                matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(MASKED_VALUE));
            } else {
                matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(email));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replacePlainSensitiveFields(String value) {
        java.util.regex.Matcher matcher = PLAIN_SENSITIVE_FIELD.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(
                    buffer,
                    java.util.regex.Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + MASKED_VALUE)
            );
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

}
