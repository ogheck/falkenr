package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.ConfigComparisonDescriptor;
import com.devtools.ui.core.model.ConfigDiffEntryDescriptor;
import com.devtools.ui.core.model.ConfigDriftDescriptor;
import com.devtools.ui.core.model.ConfigPropertyDescriptor;
import com.devtools.ui.core.model.ConfigSnapshotDescriptor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

class JsonFileConfigSnapshotStore {

    private static final TypeReference<List<ConfigSnapshotDescriptor>> SNAPSHOT_LIST = new TypeReference<>() {
    };

    private final Path file;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxSnapshots;
    private final List<ConfigSnapshotDescriptor> snapshots;

    JsonFileConfigSnapshotStore(Path file, ObjectMapper objectMapper, Clock clock, int maxSnapshots) {
        this.file = file;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxSnapshots = Math.max(1, maxSnapshots);
        this.snapshots = new ArrayList<>(trimToRetention(loadSnapshots()));
    }

    synchronized List<ConfigSnapshotDescriptor> snapshots() {
        return snapshots.stream()
                .sorted(Comparator.comparing(ConfigSnapshotDescriptor::capturedAt).reversed())
                .toList();
    }

    synchronized ConfigSnapshotDescriptor createSnapshot(String label, List<ConfigPropertyDescriptor> properties) {
        ConfigSnapshotDescriptor snapshot = new ConfigSnapshotDescriptor(
                "cfg_" + UUID.randomUUID().toString().replace("-", ""),
                label == null || label.isBlank() ? "Snapshot " + Instant.now(clock) : label.trim(),
                Instant.now(clock).toString(),
                List.copyOf(properties)
        );
        snapshots.add(snapshot);
        trimInMemorySnapshots();
        persistSnapshots();
        return snapshot;
    }

    synchronized ConfigComparisonDescriptor compare(String snapshotId, List<ConfigPropertyDescriptor> currentProperties) {
        ConfigSnapshotDescriptor snapshot = snapshots.stream()
                .filter(candidate -> candidate.snapshotId().equals(snapshotId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown config snapshot: " + snapshotId));
        return compare(snapshot, currentProperties);
    }

    synchronized ConfigDriftDescriptor drift(String snapshotId, List<ConfigPropertyDescriptor> currentProperties) {
        ConfigSnapshotDescriptor snapshot = snapshotId == null || snapshotId.isBlank()
                ? latestSnapshot()
                : snapshots.stream()
                .filter(candidate -> candidate.snapshotId().equals(snapshotId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown config snapshot: " + snapshotId));
        if (snapshot == null) {
            return new ConfigDriftDescriptor(
                    false,
                    Instant.now(clock).toString(),
                    null,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of()
            );
        }
        ConfigComparisonDescriptor comparison = compare(snapshot, currentProperties);
        int totalChanges = comparison.addedCount() + comparison.removedCount() + comparison.changedCount();
        return new ConfigDriftDescriptor(
                true,
                Instant.now(clock).toString(),
                snapshot,
                totalChanges > 0,
                totalChanges,
                comparison.addedCount(),
                comparison.removedCount(),
                comparison.changedCount(),
                comparison.unchangedCount(),
                comparison.entries()
        );
    }

    synchronized ConfigSnapshotDescriptor latestSnapshot() {
        return snapshots.stream()
                .max(Comparator.comparing(ConfigSnapshotDescriptor::capturedAt))
                .orElse(null);
    }

    private ConfigComparisonDescriptor compare(ConfigSnapshotDescriptor snapshot, List<ConfigPropertyDescriptor> currentProperties) {
        Map<String, ConfigPropertyDescriptor> currentByKey = toMap(currentProperties);
        Map<String, ConfigPropertyDescriptor> snapshotByKey = toMap(snapshot.properties());
        List<String> keys = new ArrayList<>(currentByKey.keySet());
        snapshotByKey.keySet().stream()
                .filter(key -> !currentByKey.containsKey(key))
                .forEach(keys::add);
        keys.sort(String::compareTo);

        List<ConfigDiffEntryDescriptor> entries = keys.stream()
                .map(key -> toEntry(key, currentByKey.get(key), snapshotByKey.get(key)))
                .toList();

        return new ConfigComparisonDescriptor(
                snapshot,
                count(entries, "ADDED"),
                count(entries, "REMOVED"),
                count(entries, "CHANGED"),
                count(entries, "UNCHANGED"),
                entries
        );
    }

    private Map<String, ConfigPropertyDescriptor> toMap(List<ConfigPropertyDescriptor> properties) {
        Map<String, ConfigPropertyDescriptor> byKey = new LinkedHashMap<>();
        properties.forEach(property -> byKey.put(property.key(), property));
        return byKey;
    }

    private ConfigDiffEntryDescriptor toEntry(String key,
                                              ConfigPropertyDescriptor current,
                                              ConfigPropertyDescriptor snapshot) {
        String status;
        if (snapshot == null) {
            status = "ADDED";
        } else if (current == null) {
            status = "REMOVED";
        } else if (!Objects.equals(current.value(), snapshot.value())
                || !Objects.equals(current.propertySource(), snapshot.propertySource())) {
            status = "CHANGED";
        } else {
            status = "UNCHANGED";
        }

        return new ConfigDiffEntryDescriptor(
                key,
                status,
                current == null ? "" : current.value(),
                current == null ? "" : current.propertySource(),
                snapshot == null ? "" : snapshot.value(),
                snapshot == null ? "" : snapshot.propertySource()
        );
    }

    private int count(List<ConfigDiffEntryDescriptor> entries, String status) {
        return (int) entries.stream().filter(entry -> status.equals(entry.status())).count();
    }

    private List<ConfigSnapshotDescriptor> loadSnapshots() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(file.toFile(), SNAPSHOT_LIST);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load config snapshots from " + file, exception);
        }
    }

    private void persistSnapshots() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), trimToRetention(snapshots));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist config snapshots to " + file, exception);
        }
    }

    private void trimInMemorySnapshots() {
        if (snapshots.size() <= maxSnapshots) {
            return;
        }
        int removeCount = snapshots.size() - maxSnapshots;
        snapshots.subList(0, removeCount).clear();
    }

    private List<ConfigSnapshotDescriptor> trimToRetention(List<ConfigSnapshotDescriptor> items) {
        if (items.size() <= maxSnapshots) {
            return List.copyOf(items);
        }
        return List.copyOf(items.subList(items.size() - maxSnapshots, items.size()));
    }
}
