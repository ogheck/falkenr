package com.devtools.ui.autoconfigure;

import java.util.Map;

record ApiTestRequest(
        String method,
        String path,
        String body,
        Map<String, String> headers
) {
}
