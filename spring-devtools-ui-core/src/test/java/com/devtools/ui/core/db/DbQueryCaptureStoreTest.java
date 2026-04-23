package com.devtools.ui.core.db;

import com.devtools.ui.core.model.DbQueryDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DbQueryCaptureStoreTest {

    @Test
    void snapshotReturnsNewestEntriesFirstAndHonorsCapacity() {
        DbQueryCaptureStore store = new DbQueryCaptureStore(2);

        store.append(query("select 1", Instant.parse("2026-04-07T20:00:00Z")));
        store.append(query("select 2", Instant.parse("2026-04-07T20:01:00Z")));
        store.append(query("select 3", Instant.parse("2026-04-07T20:02:00Z")));

        assertThat(store.snapshot())
                .extracting(DbQueryDescriptor::sql)
                .containsExactly("select 3", "select 2");
    }

    @Test
    void zeroCapacityDisablesDbQueryRetention() {
        DbQueryCaptureStore store = new DbQueryCaptureStore(0);

        store.append(query("select ignored", Instant.parse("2026-04-07T20:00:00Z")));

        assertThat(store.snapshot()).isEmpty();
    }

    private DbQueryDescriptor query(String sql, Instant timestamp) {
        return new DbQueryDescriptor(timestamp, sql, "select", "dataSource", null);
    }
}
