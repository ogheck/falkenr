package com.devtools.ui.core.requests;

import com.devtools.ui.core.model.CapturedRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCaptureStoreTest {

    @Test
    void snapshotReturnsNewestEntriesFirstAndHonorsCapacity() {
        RequestCaptureStore store = new RequestCaptureStore(2);

        store.append(request("/first", 200, Instant.parse("2026-04-07T20:00:00Z")));
        store.append(request("/second", 201, Instant.parse("2026-04-07T20:01:00Z")));
        store.append(request("/third", 202, Instant.parse("2026-04-07T20:02:00Z")));

        assertThat(store.snapshot())
                .extracting(CapturedRequest::path)
                .containsExactly("/third", "/second");
    }

    @Test
    void snapshotNeverGrowsBeyondConfiguredMemoryCap() {
        RequestCaptureStore store = new RequestCaptureStore(3);

        for (int index = 0; index < 10; index++) {
            store.append(request("/request-" + index, 200 + index, Instant.parse("2026-04-07T20:00:00Z").plusSeconds(index)));
        }

        assertThat(store.snapshot())
                .hasSize(3)
                .extracting(CapturedRequest::path)
                .containsExactly("/request-9", "/request-8", "/request-7");
    }

    @Test
    void zeroCapacityDisablesRequestRetention() {
        RequestCaptureStore store = new RequestCaptureStore(0);

        store.append(request("/disabled", 204, Instant.parse("2026-04-07T20:00:00Z")));

        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void findByIdReturnsCapturedRequestWhenPresent() {
        RequestCaptureStore store = new RequestCaptureStore(2);
        CapturedRequest request = request("/lookup", 200, Instant.parse("2026-04-07T20:00:00Z"));

        store.append(request);

        assertThat(store.findById(request.requestId())).isEqualTo(request);
        assertThat(store.findById("missing")).isNull();
    }

    @Test
    void restoresAndPersistsCapturedRequestsWhenPersistenceIsConfigured() {
        AtomicReference<List<CapturedRequest>> persisted = new AtomicReference<>(List.of(
                request("/persisted-1", 200, Instant.parse("2026-04-07T20:00:00Z")),
                request("/persisted-2", 201, Instant.parse("2026-04-07T20:01:00Z"))
        ));
        RequestCapturePersistence persistence = new RequestCapturePersistence() {
            @Override
            public List<CapturedRequest> load() {
                return persisted.get();
            }

            @Override
            public void save(List<CapturedRequest> requests) {
                persisted.set(List.copyOf(requests));
            }
        };

        RequestCaptureStore store = new RequestCaptureStore(2, persistence);

        assertThat(store.snapshot())
                .extracting(CapturedRequest::path)
                .containsExactly("/persisted-2", "/persisted-1");

        store.append(request("/persisted-3", 202, Instant.parse("2026-04-07T20:02:00Z")));

        assertThat(persisted.get())
                .extracting(CapturedRequest::path)
                .containsExactly("/persisted-2", "/persisted-3");
    }

    private CapturedRequest request(String path, int status, Instant timestamp) {
        return new CapturedRequest("req-" + path, "GET", path, Map.of(), "", false, false, timestamp, status);
    }
}
