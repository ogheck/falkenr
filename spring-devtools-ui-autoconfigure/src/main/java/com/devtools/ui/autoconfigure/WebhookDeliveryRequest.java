package com.devtools.ui.autoconfigure;

import java.util.Map;

record WebhookDeliveryRequest(
        String path,
        String body,
        Map<String, String> headers
) {
}
