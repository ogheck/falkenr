package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.ConfigPropertyDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFileConfigSnapshotStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void retainsOnlyConfiguredSnapshotCount() {
        JsonFileConfigSnapshotStore store = new JsonFileConfigSnapshotStore(
                tempDir.resolve("config-snapshots.json"),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-09T12:00:00Z"), ZoneOffset.UTC),
                2
        );

        store.createSnapshot("one", List.of(new ConfigPropertyDescriptor("alpha", "1", "application.yml")));
        store.createSnapshot("two", List.of(new ConfigPropertyDescriptor("alpha", "2", "application.yml")));
        store.createSnapshot("three", List.of(new ConfigPropertyDescriptor("alpha", "3", "application.yml")));

        List<String> labels = store.snapshots().stream().map(snapshot -> snapshot.label()).toList();
        assertThat(labels).containsExactlyInAnyOrder("three", "two");
    }

    @Test
    void driftUsesLatestSnapshotAndReportsChanges() {
        JsonFileConfigSnapshotStore store = new JsonFileConfigSnapshotStore(
                tempDir.resolve("config-snapshots.json"),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-09T12:00:00Z"), ZoneOffset.UTC),
                2
        );

        store.createSnapshot("baseline", List.of(
                new ConfigPropertyDescriptor("alpha", "1", "application.yml"),
                new ConfigPropertyDescriptor("beta", "2", "application.yml")
        ));

        var drift = store.drift(null, List.of(
                new ConfigPropertyDescriptor("alpha", "9", "application.yml"),
                new ConfigPropertyDescriptor("gamma", "3", "application.yml")
        ));

        assertThat(drift.available()).isTrue();
        assertThat(drift.snapshot().label()).isEqualTo("baseline");
        assertThat(drift.drifted()).isTrue();
        assertThat(drift.totalChanges()).isEqualTo(3);
        assertThat(drift.changedCount()).isEqualTo(1);
        assertThat(drift.addedCount()).isEqualTo(1);
        assertThat(drift.removedCount()).isEqualTo(1);
    }
}
