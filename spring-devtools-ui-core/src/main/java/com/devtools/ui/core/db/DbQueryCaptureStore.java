package com.devtools.ui.core.db;

import com.devtools.ui.core.model.DbQueryDescriptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class DbQueryCaptureStore {

    private final int capacity;
    private final Deque<DbQueryDescriptor> queries;

    public DbQueryCaptureStore(int capacity) {
        this.capacity = capacity;
        this.queries = new ArrayDeque<>(Math.max(1, capacity));
    }

    public synchronized void append(DbQueryDescriptor query) {
        if (capacity <= 0) {
            return;
        }
        if (queries.size() >= capacity) {
            queries.removeFirst();
        }
        queries.addLast(query);
    }

    public synchronized List<DbQueryDescriptor> snapshot() {
        List<DbQueryDescriptor> snapshot = new ArrayList<>(queries);
        Collections.reverse(snapshot);
        return snapshot;
    }
}
