package com.devtools.ui.core.policy;

import java.util.List;
import java.util.Map;

public interface DevToolsDataPolicy {

    boolean shouldCaptureRequest(String path);

    boolean captureRequestHeaders();

    boolean captureRequestBodies();

    boolean truncateRequestBodies();

    String sanitizeConfigValue(String key, String value);

    Map<String, List<String>> sanitizeHeaders(Map<String, List<String>> headers);

    String sanitizeSql(String sql);

    String sanitizeScheduledValue(String value);

    String sanitizePayload(String value);

    boolean maskSessionSecrets();

    String sanitizeSessionSecret(String value);

    String maskValue();
}
