package com.devtools.ui.core.logs;

import com.devtools.ui.core.model.LogEventDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLogStoreTest {

    @Test
    void snapshotReturnsNewestEntriesFirstAndHonorsCapacity() {
        InMemoryLogStore store = new InMemoryLogStore(2);

        store.append(event("first", Instant.parse("2026-04-07T20:00:00Z")));
        store.append(event("second", Instant.parse("2026-04-07T20:01:00Z")));
        store.append(event("third", Instant.parse("2026-04-07T20:02:00Z")));

        assertThat(store.snapshot())
                .extracting(LogEventDescriptor::message)
                .containsExactly("third", "second");
    }

    @Test
    void zeroCapacityDisablesLogRetention() {
        InMemoryLogStore store = new InMemoryLogStore(0);

        store.append(event("ignored", Instant.parse("2026-04-07T20:00:00Z")));

        assertThat(store.snapshot()).isEmpty();
    }

    private LogEventDescriptor event(String message, Instant timestamp) {
        return new LogEventDescriptor(timestamp, "INFO", message, "test.logger", null);
    }
}
