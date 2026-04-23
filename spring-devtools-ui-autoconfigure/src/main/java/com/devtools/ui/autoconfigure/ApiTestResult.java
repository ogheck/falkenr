package com.devtools.ui.autoconfigure;

record ApiTestResult(
        String method,
        String path,
        int status,
        String responseBody
) {
}
