package com.devtools.ui.core.logs;

import com.devtools.ui.core.model.LogEventDescriptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class InMemoryLogStore {

    private final int capacity;
    private final Deque<LogEventDescriptor> events;

    public InMemoryLogStore(int capacity) {
        this.capacity = capacity;
        this.events = new ArrayDeque<>(Math.max(1, capacity));
    }

    public synchronized void append(LogEventDescriptor event) {
        if (capacity <= 0) {
            return;
        }
        if (events.size() >= capacity) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    public synchronized List<LogEventDescriptor> snapshot() {
        List<LogEventDescriptor> snapshot = new ArrayList<>(events);
        Collections.reverse(snapshot);
        return snapshot;
    }
}
