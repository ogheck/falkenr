package com.devtools.ui.autoconfigure;

interface RelayClient {

    RelayAttachResult attach(RelayAttachRequest request);

    RelayHeartbeatResult heartbeat(RelayHeartbeatRequest request);

    RelaySyncResult sync(RelaySyncRequest request);

    RelayTunnelOpenResult openTunnel(RelayTunnelOpenRequest request);

    RelayTunnelCloseResult closeTunnel(RelayTunnelCloseRequest request);

    void registerAccessToken(RelayAccessTokenRequest request);

    void registerRequestArtifact(RelayRequestArtifactRequest request);

    void registerSessionCollaboration(RelaySessionCollaborationRequest request);

    java.util.List<com.devtools.ui.core.model.RelayViewerSessionDescriptor> viewerSessions(String sessionId, String connectionId);

    void revokeViewerSession(String sessionId, String connectionId, String viewerSessionId);

    com.devtools.ui.core.model.RelaySessionIdentityDescriptor sessionIdentity(String sessionId, String connectionId);

    com.devtools.ui.core.model.RelaySessionIdentityDescriptor transferOwner(String sessionId, String connectionId, String targetViewerSessionId);
}
