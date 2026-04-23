package com.devtools.ui.core.model;

import java.util.List;

public record ConfigDriftDescriptor(
        boolean available,
        String comparedAt,
        ConfigSnapshotDescriptor snapshot,
        boolean drifted,
        int totalChanges,
        int addedCount,
        int removedCount,
        int changedCount,
        int unchangedCount,
        List<ConfigDiffEntryDescriptor> entries
) {
}
