package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.HostedSessionViewDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class InMemoryHostedRelaySessionStore implements HostedRelaySessionStore {

    private final Map<String, HostedSessionViewDescriptor> currentViews = new LinkedHashMap<>();
    private final Map<String, List<HostedSessionViewDescriptor>> historyViews = new LinkedHashMap<>();

    @Override
    public synchronized HostedSessionViewDescriptor current(String sessionId) {
        return currentViews.get(sessionId);
    }

    @Override
    public synchronized List<HostedSessionViewDescriptor> history(String sessionId) {
        return List.copyOf(historyViews.getOrDefault(sessionId, List.of()));
    }

    @Override
    public synchronized void storePublished(HostedSessionViewDescriptor view) {
        currentViews.put(view.sessionId(), view);
        List<HostedSessionViewDescriptor> history = historyViews.computeIfAbsent(view.sessionId(), ignored -> new ArrayList<>());
        history.add(0, view);
        while (history.size() > 10) {
            history.remove(history.size() - 1);
        }
    }

    @Override
    public synchronized void storeCurrent(HostedSessionViewDescriptor view) {
        currentViews.put(view.sessionId(), view);
    }

    @Override
    public synchronized void clear(String sessionId) {
        currentViews.remove(sessionId);
        historyViews.remove(sessionId);
    }
}
