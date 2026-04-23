package com.devtools.ui.autoconfigure;

record FeatureFlagUpdateRequest(
        String key,
        boolean enabled
) {
}
