package com.devtools.ui.core.model;

public record ConfigPropertyDescriptor(
        String key,
        String value,
        String propertySource
) {
}
