package com.devtools.ui.core.model;

import java.util.List;

public record ConfigComparisonDescriptor(
        ConfigSnapshotDescriptor snapshot,
        int addedCount,
        int removedCount,
        int changedCount,
        int unchangedCount,
        List<ConfigDiffEntryDescriptor> entries
) {
}
