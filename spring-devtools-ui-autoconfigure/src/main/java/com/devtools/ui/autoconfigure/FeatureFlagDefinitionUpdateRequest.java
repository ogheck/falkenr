package com.devtools.ui.autoconfigure;

import java.util.List;

record FeatureFlagDefinitionUpdateRequest(
        String key,
        String displayName,
        String description,
        String owner,
        List<String> tags,
        String lifecycle,
        Boolean allowOverride
) {
}
