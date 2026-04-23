package com.devtools.ui.core.model;

public record FeatureFlagDescriptor(
        String key,
        boolean enabled,
        String propertySource,
        boolean overridden,
        FeatureFlagDefinitionDescriptor definition
) {
}
