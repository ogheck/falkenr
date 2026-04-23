package com.devtools.ui.autoconfigure;

enum DevToolsPermission {
    CONFIG_WRITE("config.write"),
    FEATURE_FLAGS_WRITE("feature-flags.write"),
    FAKE_SERVICES_WRITE("fake-services.write"),
    TIME_WRITE("time.write"),
    SESSION_CONTROL("session.control"),
    WEBHOOKS_SEND("webhooks.send"),
    API_TESTING_SEND("api-testing.send"),
    APPROVALS_REVIEW("approvals.review");

    private final String value;

    DevToolsPermission(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
