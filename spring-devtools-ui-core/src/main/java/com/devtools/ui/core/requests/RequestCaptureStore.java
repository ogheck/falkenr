package com.devtools.ui.core.requests;

import com.devtools.ui.core.model.CapturedRequest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class RequestCaptureStore {

    private final int capacity;
    private final Deque<CapturedRequest> requests;
    private final RequestCapturePersistence persistence;

    public RequestCaptureStore(int capacity) {
        this(capacity, null);
    }

    public RequestCaptureStore(int capacity, RequestCapturePersistence persistence) {
        this.capacity = capacity;
        this.requests = new ArrayDeque<>(Math.max(1, capacity));
        this.persistence = persistence;
        restorePersistedRequests();
    }

    public synchronized void append(CapturedRequest request) {
        if (capacity <= 0) {
            return;
        }
        appendInternal(request);
        persist();
    }

    public synchronized List<CapturedRequest> snapshot() {
        List<CapturedRequest> snapshot = new ArrayList<>(requests);
        Collections.reverse(snapshot);
        return snapshot;
    }

    public synchronized CapturedRequest findById(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requests.stream()
                .filter(request -> requestId.equals(request.requestId()))
                .findFirst()
                .orElse(null);
    }

    private void restorePersistedRequests() {
        if (persistence == null || capacity <= 0) {
            return;
        }
        List<CapturedRequest> persistedRequests = persistence.load();
        if (persistedRequests == null || persistedRequests.isEmpty()) {
            return;
        }
        persistedRequests.forEach(this::appendInternal);
    }

    private void appendInternal(CapturedRequest request) {
        if (requests.size() >= capacity) {
            requests.removeFirst();
        }
        requests.addLast(request);
    }

    private void persist() {
        if (persistence == null) {
            return;
        }
        persistence.save(List.copyOf(requests));
    }
}
