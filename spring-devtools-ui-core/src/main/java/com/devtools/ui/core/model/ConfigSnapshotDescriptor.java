package com.devtools.ui.core.model;

import java.util.List;

public record ConfigSnapshotDescriptor(
        String snapshotId,
        String label,
        String capturedAt,
        List<ConfigPropertyDescriptor> properties
) {
}
