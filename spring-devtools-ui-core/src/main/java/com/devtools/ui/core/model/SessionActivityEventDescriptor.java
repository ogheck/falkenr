package com.devtools.ui.core.model;

public record SessionActivityEventDescriptor(
        String eventType,
        String actor,
        String detail,
        String timestamp
) {
}
