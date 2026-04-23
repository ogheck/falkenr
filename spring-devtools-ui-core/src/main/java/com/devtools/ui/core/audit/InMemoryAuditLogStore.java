package com.devtools.ui.core.audit;

import com.devtools.ui.core.model.AuditLogEventDescriptor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryAuditLogStore {

    private final int limit;
    private final Clock clock;
    private final List<AuditLogEventDescriptor> events = new ArrayList<>();

    public InMemoryAuditLogStore(int limit, Clock clock) {
        this.limit = Math.max(0, limit);
        this.clock = clock;
    }

    public synchronized void record(String category, String action, String actor, String detail) {
        if (limit == 0) {
            return;
        }
        events.add(0, new AuditLogEventDescriptor(
                "auditlog-" + UUID.randomUUID(),
                category,
                action,
                actor == null || actor.isBlank() ? "local-operator" : actor.trim(),
                detail == null ? "" : detail,
                Instant.now(clock).toString()
        ));
        trim();
    }

    public synchronized List<AuditLogEventDescriptor> snapshot() {
        return List.copyOf(events);
    }

    private void trim() {
        while (events.size() > limit) {
            events.remove(events.size() - 1);
        }
    }
}
