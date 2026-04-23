package com.devtools.ui.core.model;

import java.util.List;

public record FeatureFlagDefinitionDescriptor(
        String key,
        String displayName,
        String description,
        String owner,
        List<String> tags,
        String lifecycle,
        boolean allowOverride,
        boolean persisted,
        String lastModifiedAt,
        String lastModifiedBy
) {
}
