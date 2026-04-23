package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.HostedSessionViewDescriptor;

import java.util.List;

interface HostedRelaySessionStore {

    HostedSessionViewDescriptor current(String sessionId);

    List<HostedSessionViewDescriptor> history(String sessionId);

    void storePublished(HostedSessionViewDescriptor view);

    void storeCurrent(HostedSessionViewDescriptor view);

    void clear(String sessionId);
}
