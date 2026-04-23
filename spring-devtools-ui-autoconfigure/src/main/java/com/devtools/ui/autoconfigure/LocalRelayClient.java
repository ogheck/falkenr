package com.devtools.ui.autoconfigure;

import java.util.UUID;

class LocalRelayClient implements RelayClient {

    private final java.util.Map<String, java.util.List<com.devtools.ui.core.model.RelayViewerSessionDescriptor>> viewerSessions =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<String, com.devtools.ui.core.model.RelaySessionIdentityDescriptor> identities =
            new java.util.LinkedHashMap<>();

    private final DevToolsUiProperties.RemoteSettings remoteSettings;

    LocalRelayClient(DevToolsUiProperties.RemoteSettings remoteSettings) {
        this.remoteSettings = remoteSettings;
    }

    @Override
    public RelayAttachResult attach(RelayAttachRequest request) {
        if (remoteSettings.isSimulateAttachFailure()) {
            throw new IllegalStateException("Managed relay handshake failed for " + request.sessionId());
        }
        String organizationId = "org-local-" + slug(request.ownerName());
        String organizationName = request.ownerName() + " Workspace";
        String ownerAccountId = "acct-local-" + slug(request.ownerName());
        identities.put(
                request.sessionId(),
                new com.devtools.ui.core.model.RelaySessionIdentityDescriptor(
                        request.sessionId(),
                        new com.devtools.ui.core.model.RelayOrganizationDescriptor(organizationId, organizationName),
                        new com.devtools.ui.core.model.RelayAccountDescriptor(ownerAccountId, request.ownerName(), organizationId, "owner"),
                        java.util.List.of()
                )
        );
        return new RelayAttachResult(
                "hs_" + UUID.randomUUID(),
                "conn_" + UUID.randomUUID(),
                "connected",
                "lease_" + UUID.randomUUID(),
                java.time.Instant.now().plusSeconds(300).toString(),
                request.shareUrl() + "/viewer",
                organizationId,
                organizationName,
                ownerAccountId
        );
    }

    @Override
    public RelayHeartbeatResult heartbeat(RelayHeartbeatRequest request) {
        if (remoteSettings.isSimulateHeartbeatFailure()) {
            return new RelayHeartbeatResult(
                    "degraded",
                    "stale",
                    false,
                    null,
                    java.time.Instant.now().plusSeconds(60).toString()
            );
        }
        return new RelayHeartbeatResult(
                "connected",
                "healthy",
                true,
                "lease_" + UUID.randomUUID(),
                java.time.Instant.now().plusSeconds(300).toString()
        );
    }

    @Override
    public RelaySyncResult sync(RelaySyncRequest request) {
        if (remoteSettings.isSimulateHeartbeatFailure()) {
            throw new IllegalStateException("Managed relay sync failed for " + request.sessionId());
        }
        return new RelaySyncResult(
                "sync_" + UUID.randomUUID(),
                "view_" + UUID.randomUUID(),
                "connected",
                true
        );
    }

    @Override
    public RelayTunnelOpenResult openTunnel(RelayTunnelOpenRequest request) {
        if (remoteSettings.isSimulateAttachFailure()) {
            throw new IllegalStateException("Managed relay tunnel open failed for " + request.sessionId());
        }
        return new RelayTunnelOpenResult(
                "tunnel_" + UUID.randomUUID(),
                "open",
                java.time.Instant.now().toString(),
                request.sessionId() + "/stream"
        );
    }

    @Override
    public RelayTunnelCloseResult closeTunnel(RelayTunnelCloseRequest request) {
        return new RelayTunnelCloseResult(
                "closed",
                java.time.Instant.now().toString()
        );
    }

    @Override
    public void registerAccessToken(RelayAccessTokenRequest request) {
        // Local stub mode has no hosted relay surface to enforce access tokens against.
    }

    @Override
    public void registerRequestArtifact(RelayRequestArtifactRequest request) {
        // Local stub mode has no hosted relay surface to persist cross-machine artifacts in.
    }

    @Override
    public void registerSessionCollaboration(RelaySessionCollaborationRequest request) {
        // Local stub mode has no hosted relay surface to persist cross-machine collaboration state in.
    }

    @Override
    public java.util.List<com.devtools.ui.core.model.RelayViewerSessionDescriptor> viewerSessions(String sessionId, String connectionId) {
        return java.util.List.copyOf(viewerSessions.getOrDefault(sessionId, java.util.List.of()));
    }

    @Override
    public void revokeViewerSession(String sessionId, String connectionId, String viewerSessionId) {
        viewerSessions.computeIfPresent(sessionId, (ignored, sessions) -> sessions.stream()
                .filter(session -> !session.viewerSessionId().equals(viewerSessionId))
                .toList());
    }

    @Override
    public com.devtools.ui.core.model.RelaySessionIdentityDescriptor sessionIdentity(String sessionId, String connectionId) {
        return identities.getOrDefault(
                sessionId,
                new com.devtools.ui.core.model.RelaySessionIdentityDescriptor(
                        sessionId,
                        new com.devtools.ui.core.model.RelayOrganizationDescriptor("org-local", "Local Workspace"),
                        new com.devtools.ui.core.model.RelayAccountDescriptor("acct-local-owner", "local-developer", "org-local", "owner"),
                        java.util.List.of()
                )
        );
    }

    @Override
    public com.devtools.ui.core.model.RelaySessionIdentityDescriptor transferOwner(String sessionId, String connectionId, String targetViewerSessionId) {
        com.devtools.ui.core.model.RelaySessionIdentityDescriptor currentIdentity = sessionIdentity(sessionId, connectionId);
        com.devtools.ui.core.model.RelayViewerSessionDescriptor target = viewerSessions(sessionId, connectionId).stream()
                .filter(viewerSession -> viewerSession.viewerSessionId().equals(targetViewerSessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown relay viewer session: " + targetViewerSessionId));
        java.util.List<com.devtools.ui.core.model.RelayAccountDescriptor> collaborators = viewerSessions(sessionId, connectionId).stream()
                .filter(viewerSession -> !viewerSession.viewerSessionId().equals(targetViewerSessionId))
                .map(viewerSession -> new com.devtools.ui.core.model.RelayAccountDescriptor(
                        "acct-viewer-" + viewerSession.viewerSessionId(),
                        viewerSession.viewerName(),
                        currentIdentity.organization().organizationId(),
                        viewerSession.role()
                ))
                .toList();
        com.devtools.ui.core.model.RelaySessionIdentityDescriptor updated = new com.devtools.ui.core.model.RelaySessionIdentityDescriptor(
                sessionId,
                currentIdentity.organization(),
                new com.devtools.ui.core.model.RelayAccountDescriptor(
                        "acct-viewer-" + target.viewerSessionId(),
                        target.viewerName(),
                        currentIdentity.organization().organizationId(),
                        "owner"
                ),
                collaborators
        );
        identities.put(sessionId, updated);
        return updated;
    }

    private String slug(String value) {
        String slug = value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "local" : slug;
    }
}
