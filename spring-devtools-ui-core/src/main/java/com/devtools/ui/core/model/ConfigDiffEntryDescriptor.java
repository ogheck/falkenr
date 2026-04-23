package com.devtools.ui.core.model;

public record ConfigDiffEntryDescriptor(
        String key,
        String status,
        String currentValue,
        String currentPropertySource,
        String snapshotValue,
        String snapshotPropertySource
) {
}
