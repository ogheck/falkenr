package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.RemoteSessionDescriptor;
import com.devtools.ui.core.model.RelaySessionIdentityDescriptor;
import com.devtools.ui.core.model.RelayViewerSessionDescriptor;
import com.devtools.ui.core.model.SessionAccessValidationDescriptor;
import com.devtools.ui.core.model.SessionReplayEntryDescriptor;
import com.devtools.ui.core.model.SessionShareTokenDescriptor;
import com.devtools.ui.core.model.CapturedRequest;

import java.util.List;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

class RemoteSessionService {

    private final LocalAgentSessionStore sessionStore;
    private final RelayClient relayClient;
    private final RelayTokenCodec relayTokenCodec;
    private final HostedRelaySessionStore hostedRelaySessionStore;
    private final TunnelStreamClient tunnelStreamClient;
    private TunnelStreamClient.TunnelStreamHandle tunnelStreamHandle;

    RemoteSessionService(LocalAgentSessionStore sessionStore,
                         RelayClient relayClient,
                         RelayTokenCodec relayTokenCodec,
                         HostedRelaySessionStore hostedRelaySessionStore,
                         TunnelStreamClient tunnelStreamClient) {
        this.sessionStore = sessionStore;
        this.relayClient = relayClient;
        this.relayTokenCodec = relayTokenCodec;
        this.hostedRelaySessionStore = hostedRelaySessionStore;
        this.tunnelStreamClient = tunnelStreamClient;
    }

    RemoteSessionDescriptor snapshot() {
        return sessionStore.snapshot();
    }

    RemoteSessionDescriptor attach(RemoteSessionAttachRequest request) {
        LocalAgentSessionStore.SessionState state = sessionStore.attach(request);
        hostedRelaySessionStore.clear(state.sessionId());
        connectRelay(state);
        return snapshot();
    }

    RemoteSessionDescriptor rotate() {
        LocalAgentSessionStore.SessionState state = sessionStore.rotate();
        connectRelay(state);
        return snapshot();
    }

    RemoteSessionDescriptor heartbeat() {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor beforeHeartbeat = snapshot();
        if (state.connectionId() == null || !beforeHeartbeat.attached()) {
            return snapshot();
        }
        boolean shouldReconnectTunnel = shouldReconnectTunnel(beforeHeartbeat);
        RelayHeartbeatResult result = relayClient.heartbeat(new RelayHeartbeatRequest(
                state.sessionId(),
                state.connectionId()
        ));
        sessionStore.markHeartbeat(result);
        if (!result.connected()) {
            sessionStore.scheduleReconnect();
            closeTunnelStream();
        } else if (shouldReconnectTunnel) {
            reopenTunnel(state);
        }
        return snapshot();
    }

    RemoteSessionDescriptor openTunnel() {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor snapshot = snapshot();
        if (state.connectionId() == null || !snapshot.attached()) {
            return snapshot;
        }
        try {
            RelayTunnelOpenResult result = relayClient.openTunnel(new RelayTunnelOpenRequest(
                    state.sessionId(),
                    state.connectionId()
            ));
            sessionStore.markTunnelOpened(result);
            connectTunnelStream(result.streamUrl());
        } catch (RuntimeException exception) {
            sessionStore.markTunnelOpenFailure(exception.getMessage());
        }
        return snapshot();
    }

    RemoteSessionDescriptor closeTunnel() {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor snapshot = snapshot();
        if (state.connectionId() == null || !snapshot.attached() || snapshot.relayTunnelId() == null) {
            return snapshot;
        }
        RelayTunnelCloseResult result = relayClient.closeTunnel(new RelayTunnelCloseRequest(
                state.sessionId(),
                state.connectionId(),
                snapshot.relayTunnelId()
        ));
        closeTunnelStream();
        sessionStore.markTunnelClosed(result);
        return snapshot();
    }

    void revoke() {
        closeTunnelStream();
        hostedRelaySessionStore.clear(sessionStore.snapshot().sessionId());
        sessionStore.revoke();
    }

    SessionShareTokenDescriptor issueShareToken(SessionShareRequest request) {
        SessionShareTokenDescriptor descriptor = sessionStore.issueShareToken(request.role());
        if (descriptor.active()) {
            extractToken(descriptor.shareUrl()).ifPresent(token -> {
                try {
                    relayClient.registerAccessToken(new RelayAccessTokenRequest(
                            sessionStore.snapshot().sessionId(),
                            token,
                            descriptor.role(),
                            descriptor.expiresAt()
                    ));
                } catch (RuntimeException ignored) {
                    // Token registration should not break local share-token issuance.
                }
            });
        }
        return descriptor;
    }

    SessionAccessValidationDescriptor validateShareToken(SessionValidateRequest request) {
        return sessionStore.validateShareToken(request.token());
    }

    List<SessionReplayEntryDescriptor> replay() {
        return sessionStore.replayEntries();
    }

    List<RelayViewerSessionDescriptor> viewerSessions() {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor snapshot = snapshot();
        if (state.connectionId() == null || !snapshot.attached()) {
            return List.of();
        }
        return relayClient.viewerSessions(state.sessionId(), state.connectionId());
    }

    List<RelayViewerSessionDescriptor> revokeViewerSession(String viewerSessionId) {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor snapshot = snapshot();
        if (state.connectionId() == null || !snapshot.attached()) {
            return List.of();
        }
        relayClient.revokeViewerSession(state.sessionId(), state.connectionId(), viewerSessionId);
        return relayClient.viewerSessions(state.sessionId(), state.connectionId());
    }

    RelaySessionIdentityDescriptor sessionIdentity() {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor snapshot = snapshot();
        if (state.connectionId() == null || !snapshot.attached()) {
            return new RelaySessionIdentityDescriptor(
                    snapshot.sessionId(),
                    new com.devtools.ui.core.model.RelayOrganizationDescriptor(
                            snapshot.relayOrganizationId(),
                            snapshot.relayOrganizationName()
                    ),
                    new com.devtools.ui.core.model.RelayAccountDescriptor(
                            snapshot.relayOwnerAccountId(),
                            snapshot.ownerName(),
                            snapshot.relayOrganizationId(),
                            "owner"
                    ),
                    List.of()
            );
        }
        return relayClient.sessionIdentity(state.sessionId(), state.connectionId());
    }

    RelaySessionIdentityDescriptor transferOwner(SessionOwnerTransferRequest request) {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor snapshot = snapshot();
        if (state.connectionId() == null || !snapshot.attached()) {
            throw new IllegalStateException("Session must be attached before transferring relay ownership");
        }
        RelaySessionIdentityDescriptor identity = relayClient.transferOwner(
                state.sessionId(),
                state.connectionId(),
                request.targetViewerSessionId()
        );
        sessionStore.applyRelayIdentity(identity, snapshot.ownerName());
        return identity;
    }

    com.devtools.ui.core.model.HostedSessionViewDescriptor hostedView() {
        String sessionId = sessionStore.snapshot().sessionId();
        com.devtools.ui.core.model.HostedSessionViewDescriptor hostedView = hostedRelaySessionStore.current(sessionId);
        return hostedView == null ? sessionStore.hostedView() : hostedView;
    }

    List<com.devtools.ui.core.model.HostedSessionViewDescriptor> hostedHistory() {
        return hostedRelaySessionStore.history(sessionStore.snapshot().sessionId());
    }

    com.devtools.ui.core.model.HostedSessionViewDescriptor addHostedMember(HostedSessionMemberRequest request) {
        com.devtools.ui.core.model.HostedSessionViewDescriptor hostedView = sessionStore.addHostedMember(
                request.memberId(),
                request.role(),
                request.source(),
                request.actor()
        );
        hostedRelaySessionStore.storeCurrent(hostedView);
        return hostedView;
    }

    com.devtools.ui.core.model.HostedSessionViewDescriptor removeHostedMember(String memberId, String actor) {
        com.devtools.ui.core.model.HostedSessionViewDescriptor hostedView = sessionStore.removeHostedMember(memberId, actor);
        hostedRelaySessionStore.storeCurrent(hostedView);
        return hostedView;
    }

    void recordRequestReplay(CapturedRequest request) {
        sessionStore.recordRequestReplay(request);
        RemoteSessionDescriptor snapshot = sessionStore.snapshot();
        if (!snapshot.attached() || snapshot.relayConnectionId() == null) {
            return;
        }
        try {
            relayClient.registerRequestArtifact(new RelayRequestArtifactRequest(snapshot.sessionId(), request));
        } catch (RuntimeException ignored) {
            // Relay artifact persistence should not break local request capture.
        }
    }

    RemoteSessionDescriptor focusArtifact(SessionInspectRequest request) {
        sessionStore.focusArtifact(request.artifactType(), request.artifactId(), request.actor());
        publishCollaborationSnapshotIfAttached();
        return snapshot();
    }

    RemoteSessionDescriptor addDebugNote(SessionDebugNoteRequest request) {
        sessionStore.addDebugNote(request.author(), request.message(), request.artifactType(), request.artifactId());
        publishCollaborationSnapshotIfAttached();
        return snapshot();
    }

    RemoteSessionDescriptor startRecording(SessionRecordingRequest request) {
        sessionStore.startRecording(request.actor());
        publishCollaborationSnapshotIfAttached();
        return snapshot();
    }

    RemoteSessionDescriptor stopRecording(SessionRecordingRequest request) {
        sessionStore.stopRecording(request.actor());
        publishCollaborationSnapshotIfAttached();
        return snapshot();
    }

    RemoteSessionDescriptor sync() {
        LocalAgentSessionStore.SessionState state = sessionStore.heartbeatState();
        RemoteSessionDescriptor snapshot = snapshot();
        if (state.connectionId() == null || !snapshot.attached()) {
            return snapshot;
        }
        try {
            RelaySyncResult result = relayClient.sync(new RelaySyncRequest(
                    state.sessionId(),
                    state.connectionId(),
                    snapshot
            ));
            sessionStore.markSyncSuccess(result);
            hostedRelaySessionStore.storePublished(sessionStore.hostedView());
            publishCollaborationSnapshotIfAttached();
        } catch (RuntimeException exception) {
            sessionStore.markSyncFailure(exception.getMessage());
        }
        return snapshot();
    }

    private void connectRelay(LocalAgentSessionStore.SessionState state) {
        String encryptedToken = relayTokenCodec.encode(
                state.sessionId(),
                state.ownerName(),
                state.allowedRoles(),
                state.expiresAt()
        );

        try {
            RelayAttachResult result = relayClient.attach(new RelayAttachRequest(
                    state.sessionId(),
                    state.ownerName(),
                    state.allowedRoles(),
                    state.relayUrl(),
                    state.shareUrl(),
                    encryptedToken,
                    state.expiresAt()
            ));
            sessionStore.updateRelayState(result, relayTokenCodec.preview(encryptedToken));
        } catch (RuntimeException exception) {
            sessionStore.markAttachFailure(exception.getMessage());
        }
    }

    private boolean shouldReconnectTunnel(RemoteSessionDescriptor snapshot) {
        if (snapshot.relayTunnelId() != null) {
            return false;
        }
        if (!snapshot.attached() || snapshot.reconnectAt() == null) {
            return false;
        }
        String tunnelStatus = snapshot.tunnelStatus();
        if (!"stream-failed".equals(tunnelStatus) && !"stream-closed".equals(tunnelStatus) && !"stale".equals(tunnelStatus)) {
            return false;
        }
        return !Instant.parse(snapshot.reconnectAt()).isAfter(Instant.now());
    }

    private void reopenTunnel(LocalAgentSessionStore.SessionState state) {
        try {
            RelayTunnelOpenResult result = relayClient.openTunnel(new RelayTunnelOpenRequest(
                    state.sessionId(),
                    state.connectionId()
            ));
            sessionStore.markTunnelOpened(result);
            connectTunnelStream(result.streamUrl());
        } catch (RuntimeException exception) {
            sessionStore.markTunnelOpenFailure(exception.getMessage());
        }
    }

    private java.util.Optional<String> extractToken(String shareUrl) {
        if (shareUrl == null || shareUrl.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            String query = URI.create(shareUrl).getRawQuery();
            if (query == null || query.isBlank()) {
                return java.util.Optional.empty();
            }
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
                if (!"token".equals(key)) {
                    continue;
                }
                return java.util.Optional.of(URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.empty();
    }

    private void connectTunnelStream(String streamUrl) {
        closeTunnelStream();
        if (streamUrl == null || streamUrl.isBlank()) {
            sessionStore.markTunnelStreamFailed("Missing relay tunnel stream URL");
            return;
        }
        tunnelStreamHandle = tunnelStreamClient.connect(streamUrl, new TunnelStreamClient.TunnelEventListener() {
            @Override
            public void onConnected() {
                sessionStore.markTunnelStreamConnected();
            }

            @Override
            public void onEvent(String eventName, String payload) {
                sessionStore.markTunnelStreamEvent(eventName, payload);
            }

            @Override
            public void onClosed() {
                sessionStore.markTunnelStreamClosed();
            }

            @Override
            public void onError(String message) {
                sessionStore.markTunnelStreamFailed(message);
            }
        });
    }

    private void closeTunnelStream() {
        if (tunnelStreamHandle != null) {
            tunnelStreamHandle.close();
            tunnelStreamHandle = null;
        }
    }

    private void publishCollaborationSnapshotIfAttached() {
        RemoteSessionDescriptor snapshot = sessionStore.snapshot();
        if (!snapshot.attached() || snapshot.relayConnectionId() == null) {
            return;
        }
        try {
            relayClient.registerSessionCollaboration(new RelaySessionCollaborationRequest(
                    snapshot.sessionId(),
                    sessionStore.collaborationActivity(),
                    sessionStore.collaborationDebugNotes(),
                    sessionStore.collaborationRecordings()
            ));
        } catch (RuntimeException ignored) {
            // Collaboration persistence should not break local session behavior.
        }
    }
}
