package com.devtools.ui.autoconfigure;

record WebhookDeliveryResult(
        String method,
        String path,
        int status,
        String responseBody
) {
}
