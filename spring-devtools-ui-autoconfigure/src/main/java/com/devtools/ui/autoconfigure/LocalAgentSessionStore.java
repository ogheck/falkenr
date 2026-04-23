package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import com.devtools.ui.core.model.HostedSessionMemberDescriptor;
import com.devtools.ui.core.model.RemoteSessionDescriptor;
import com.devtools.ui.core.model.SessionAccessValidationDescriptor;
import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionAuditEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionMemberDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;
import com.devtools.ui.core.model.SessionReplayEntryDescriptor;
import com.devtools.ui.core.model.SessionShareTokenDescriptor;
import com.devtools.ui.core.model.SessionWorkspaceMemberDescriptor;
import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.policy.DevToolsDataPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;

class LocalAgentSessionStore {

    private static final String DEFAULT_OWNER = "local-developer";

    private final Clock clock;
    private final DevToolsUiProperties.RemoteSettings remoteSettings;
    private final DevToolsUiProperties.SecretsSettings secretsSettings;
    private final DevToolsDataPolicy dataPolicy;
    private final String sessionId = UUID.randomUUID().toString();

    private boolean attached;
    private boolean allowGuests;
    private int viewerCount;
    private String ownerName = DEFAULT_OWNER;
    private String ownerTokenPreview = "[unavailable]";
    private String lastIssuedShareRole;
    private String lastIssuedShareTokenPreview = "[unavailable]";
    private String tokenMode = "aes-gcm";
    private String lastError;
    private String relayHandshakeId;
    private String relayConnectionId;
    private String relayLeaseId;
    private Instant relayLeaseExpiresAt;
    private String relayViewerUrl;
    private String relayOrganizationId;
    private String relayOrganizationName;
    private String relayOwnerAccountId;
    private String relayStatus = "idle";
    private String relayTunnelId;
    private String tunnelStatus = "idle";
    private Instant tunnelOpenedAt;
    private Instant tunnelClosedAt;
    private String focusedArtifactType;
    private String focusedArtifactId;
    private String focusedBy;
    private Instant focusedAt;
    private Instant lastHeartbeatAt;
    private Instant nextHeartbeatAt;
    private Instant reconnectAt;
    private Instant lastRotatedAt;
    private Instant expiresAt;
    private final Map<String, IssuedShareToken> issuedShareTokens = new LinkedHashMap<>();
    private final Map<String, ActiveMember> activeMembers = new LinkedHashMap<>();
    private final List<SessionActivityEventDescriptor> activity = new ArrayList<>();
    private final List<SessionReplayEntryDescriptor> replay = new ArrayList<>();
    private final List<SessionDebugNoteDescriptor> debugNotes = new ArrayList<>();
    private final List<SessionRecordingDescriptor> recordings = new ArrayList<>();
    private final List<SessionAuditEventDescriptor> audit = new ArrayList<>();
    private boolean recordingActive;
    private String currentRecordingId;
    private String recordingStartedBy;
    private Instant recordingStartedAt;
    private Instant recordingStoppedAt;
    private String syncStatus = "idle";
    private String lastSyncId;
    private Instant lastSyncedAt;
    private long sessionVersion;
    private Long publishedSessionVersion;
    private Instant lastPublishedAt;
    private HostedSessionViewDescriptor hostedView;
    private final List<HostedSessionViewDescriptor> hostedHistory = new ArrayList<>();
    private final Map<String, HostedMember> hostedMembers = new LinkedHashMap<>();

    LocalAgentSessionStore(Clock clock,
                           DevToolsUiProperties.RemoteSettings remoteSettings,
                           DevToolsUiProperties.SecretsSettings secretsSettings,
                           DevToolsDataPolicy dataPolicy) {
        this.clock = clock;
        this.remoteSettings = remoteSettings;
        this.secretsSettings = secretsSettings;
        this.dataPolicy = dataPolicy;
        this.lastRotatedAt = clock.instant();
        this.expiresAt = lastRotatedAt.plus(tokenTtl());
    }

    synchronized RemoteSessionDescriptor snapshot() {
        return new RemoteSessionDescriptor(
                sessionId,
                "ready",
                relayStatus,
                attached,
                sanitizedSessionValue(relayUrl()),
                sanitizedSessionValue(shareUrl()),
                List.copyOf(activity),
                List.copyOf(replay),
                List.copyOf(debugNotes),
                List.copyOf(recordings),
                List.copyOf(audit),
                allowGuests ? "owner-viewer-guest" : "owner-viewer",
                allowGuests ? List.of("owner", "viewer", "guest") : List.of("owner", "viewer"),
                activeMembers(),
                workspaceMembers(),
                attached ? 1 : 0,
                countMembersByRole("viewer"),
                countMembersByRole("guest"),
                recentActors(),
                viewerCount,
                activeShareTokens(),
                ownerName,
                relayOrganizationId,
                relayOrganizationName,
                relayOwnerAccountId,
                sanitizedTokenPreview(ownerTokenPreview),
                lastIssuedShareRole,
                sanitizedTokenPreview(lastIssuedShareTokenPreview),
                tokenMode,
                remoteSettings.getTransportMode(),
                relayHandshakeId,
                relayConnectionId,
                relayLeaseId,
                toString(relayLeaseExpiresAt),
                sanitizedSessionValue(relayViewerUrl),
                relayTunnelId,
                tunnelStatus,
                toString(tunnelOpenedAt),
                toString(tunnelClosedAt),
                sanitizedSessionValue(lastError),
                focusedArtifactType,
                focusedArtifactId,
                focusedBy,
                toString(focusedAt),
                recordingActive,
                currentRecordingId,
                toString(recordingStartedAt),
                toString(recordingStoppedAt),
                syncStatus,
                lastSyncId,
                toString(lastSyncedAt),
                sessionVersion,
                publishedSessionVersion,
                hostedView == null ? "unpublished" : "published",
                toString(lastPublishedAt),
                remoteSettings.getSessionActivityLimit(),
                remoteSettings.getSessionReplayLimit(),
                remoteSettings.getSessionRecordingLimit(),
                remoteSettings.getSessionAuditLimit(),
                toString(lastHeartbeatAt),
                toString(nextHeartbeatAt),
                toString(reconnectAt),
                lastRotatedAt.toString(),
                expiresAt.toString()
        );
    }

    synchronized SessionState attach(RemoteSessionAttachRequest request) {
        bumpVersion();
        attached = true;
        ownerName = request.ownerName() == null || request.ownerName().isBlank() ? DEFAULT_OWNER : request.ownerName().trim();
        allowGuests = request.allowGuests();
        viewerCount = 0;
        issuedShareTokens.clear();
        activeMembers.clear();
        activity.clear();
        replay.clear();
        debugNotes.clear();
        recordings.clear();
        audit.clear();
        hostedHistory.clear();
        hostedMembers.clear();
        recordingActive = false;
        currentRecordingId = null;
        recordingStartedBy = null;
        recordingStartedAt = null;
        recordingStoppedAt = null;
        syncStatus = "idle";
        lastSyncId = null;
        lastSyncedAt = null;
        lastIssuedShareRole = null;
        lastIssuedShareTokenPreview = "[unavailable]";
        rotate();
        recordActivity("session.attached", ownerName, allowGuests ? "guest access enabled" : "guest access disabled");
        recordReplay("session", "Session attached", ownerName + " attached local session");
        return state();
    }

    synchronized SessionState rotate() {
        bumpVersion();
        lastRotatedAt = clock.instant();
        expiresAt = lastRotatedAt.plus(tokenTtl());
        recordActivity("session.rotated", ownerName, "owner token rotated");
        recordReplay("token", "Owner token rotated", ownerName + " rotated owner token");
        return state();
    }

    synchronized void updateRelayState(RelayAttachResult result, String tokenPreview) {
        bumpVersion();
        relayHandshakeId = result.handshakeId();
        relayConnectionId = result.connectionId();
        relayLeaseId = result.leaseId();
        relayLeaseExpiresAt = parseInstant(result.leaseExpiresAt());
        relayViewerUrl = result.viewerUrl();
        relayOrganizationId = result.organizationId();
        relayOrganizationName = result.organizationName();
        relayOwnerAccountId = result.ownerAccountId();
        relayStatus = result.relayStatus();
        relayTunnelId = null;
        tunnelStatus = "ready";
        tunnelOpenedAt = null;
        tunnelClosedAt = null;
        lastError = null;
        ownerTokenPreview = tokenPreview;
        markHeartbeat(clock.instant());
        recordActivity("relay.connected", ownerName, result.connectionId());
        recordReplay("relay", "Relay connected", result.connectionId());
    }

    synchronized void applyRelayIdentity(com.devtools.ui.core.model.RelaySessionIdentityDescriptor identity, String actor) {
        bumpVersion();
        relayOrganizationId = identity.organization() == null ? null : identity.organization().organizationId();
        relayOrganizationName = identity.organization() == null ? null : identity.organization().organizationName();
        relayOwnerAccountId = identity.owner() == null ? null : identity.owner().accountId();
        if (identity.owner() != null && identity.owner().displayName() != null && !identity.owner().displayName().isBlank()) {
            ownerName = identity.owner().displayName();
        }
        recordActivity("relay.identity_updated", actor, relayOwnerAccountId == null ? "n/a" : relayOwnerAccountId);
        recordReplay("relay", "Relay identity updated", ownerName);
        recordAudit("relay.identity_updated", actor, ownerName);
    }

    synchronized void markAttachFailure(String message) {
        bumpVersion();
        relayStatus = "unavailable";
        tunnelStatus = "offline";
        relayHandshakeId = null;
        relayConnectionId = null;
        relayLeaseId = null;
        relayLeaseExpiresAt = null;
        relayViewerUrl = null;
        relayOrganizationId = null;
        relayOrganizationName = null;
        relayOwnerAccountId = null;
        relayTunnelId = null;
        lastHeartbeatAt = null;
        nextHeartbeatAt = null;
        reconnectAt = clock.instant().plus(reconnectDelay());
        tunnelOpenedAt = null;
        tunnelClosedAt = null;
        lastError = message;
        recordActivity("relay.attach_failed", ownerName, message);
        recordReplay("relay", "Relay attach failed", message);
    }

    synchronized SessionState heartbeatState() {
        return state();
    }

    synchronized void markHeartbeat(RelayHeartbeatResult result) {
        bumpVersion();
        Instant now = clock.instant();
        relayStatus = result.relayStatus();
        relayLeaseId = result.leaseId() == null ? relayLeaseId : result.leaseId();
        relayLeaseExpiresAt = parseInstant(result.leaseExpiresAt());
        if (result.connected()) {
            tunnelStatus = result.tunnelStatus();
            markHeartbeat(now);
            reconnectAt = null;
            lastError = null;
            recordActivity("relay.heartbeat", ownerName, "healthy");
            recordReplay("relay", "Relay heartbeat", "healthy");
            return;
        }
        tunnelStatus = result.tunnelStatus();
        lastHeartbeatAt = now;
        nextHeartbeatAt = null;
        reconnectAt = now.plus(reconnectDelay());
        lastError = "Relay heartbeat failed";
        recordActivity("relay.heartbeat_failed", ownerName, result.tunnelStatus());
        recordReplay("relay", "Relay heartbeat failed", result.tunnelStatus());
    }

    synchronized void scheduleReconnect() {
        bumpVersion();
        Instant now = clock.instant();
        relayStatus = "reconnecting";
        tunnelStatus = "stale";
        relayTunnelId = null;
        lastHeartbeatAt = now;
        nextHeartbeatAt = null;
        reconnectAt = now.plus(reconnectDelay());
        tunnelOpenedAt = null;
        tunnelClosedAt = null;
        if (lastError == null) {
            lastError = "Relay reconnect scheduled";
        }
        recordActivity("relay.reconnecting", ownerName, "reconnect scheduled");
        recordReplay("relay", "Relay reconnect scheduled", "reconnect scheduled");
    }

    synchronized void revoke() {
        bumpVersion();
        attached = false;
        allowGuests = false;
        viewerCount = 0;
        ownerName = DEFAULT_OWNER;
        ownerTokenPreview = "[unavailable]";
        lastIssuedShareRole = null;
        lastIssuedShareTokenPreview = "[unavailable]";
        lastError = null;
        focusedArtifactType = null;
        focusedArtifactId = null;
        focusedBy = null;
        focusedAt = null;
        relayHandshakeId = null;
        relayConnectionId = null;
        relayLeaseId = null;
        relayLeaseExpiresAt = null;
        relayViewerUrl = null;
        relayOrganizationId = null;
        relayOrganizationName = null;
        relayOwnerAccountId = null;
        relayTunnelId = null;
        relayStatus = "idle";
        tunnelStatus = "idle";
        tunnelOpenedAt = null;
        tunnelClosedAt = null;
        lastHeartbeatAt = null;
        nextHeartbeatAt = null;
        reconnectAt = null;
        issuedShareTokens.clear();
        activeMembers.clear();
        activity.clear();
        replay.clear();
        debugNotes.clear();
        recordings.clear();
        audit.clear();
        hostedHistory.clear();
        recordingActive = false;
        currentRecordingId = null;
        recordingStartedBy = null;
        recordingStartedAt = null;
        recordingStoppedAt = null;
        syncStatus = "idle";
        lastSyncId = null;
        lastSyncedAt = null;
        publishedSessionVersion = null;
        lastPublishedAt = null;
        hostedView = null;
        hostedMembers.clear();
        lastRotatedAt = clock.instant();
        expiresAt = lastRotatedAt.plus(tokenTtl());
    }

    synchronized void markSyncSuccess(RelaySyncResult result) {
        syncStatus = result.accepted() ? "synced" : "rejected";
        lastSyncId = result.syncId();
        lastSyncedAt = clock.instant();
        publishedSessionVersion = sessionVersion;
        lastPublishedAt = lastSyncedAt;
        List<HostedSessionMemberDescriptor> publishedMembers = hostedMembersSnapshot(lastPublishedAt);
        hostedView = buildHostedView(result.viewId(), result.accepted() ? "published" : "rejected", publishedMembers);
        hostedMembers.clear();
        publishedMembers.forEach(member -> hostedMembers.put(member.memberId(), new HostedMember(
                member.memberId(),
                member.role(),
                member.source(),
                parseInstant(member.joinedAt()),
                parseInstant(member.publishedAt()),
                member.focusedArtifactType(),
                member.focusedArtifactId(),
                member.lastAction()
        )));
        hostedHistory.add(0, hostedView);
        trimList(hostedHistory, 10);
        lastError = null;
        recordActivity("relay.synced", ownerName, lastSyncId);
        recordAudit("relay.synced", ownerName, lastSyncId);
        recordReplay("relay", "Relay sync", lastSyncId);
    }

    synchronized void markSyncFailure(String message) {
        bumpVersion();
        syncStatus = "failed";
        lastError = message;
        recordActivity("relay.sync_failed", ownerName, message);
        recordAudit("relay.sync_failed", ownerName, message);
        recordReplay("relay", "Relay sync failed", message);
    }

    synchronized void startRecording(String actor) {
        if (!attached || recordingActive) {
            return;
        }
        bumpVersion();
        recordingActive = true;
        currentRecordingId = "recording-" + UUID.randomUUID();
        recordingStartedBy = actor == null || actor.isBlank() ? ownerName : actor.trim();
        recordingStartedAt = clock.instant();
        recordingStoppedAt = null;
        upsertRecordingSnapshot(true);
        recordActivity("session.recording_started", recordingStartedBy, currentRecordingId);
        recordAudit("session.recording_started", recordingStartedBy, currentRecordingId);
        recordReplay("recording", "Session recording started", currentRecordingId);
    }

    synchronized void stopRecording(String actor) {
        if (!attached || !recordingActive) {
            return;
        }
        bumpVersion();
        String resolvedActor = actor == null || actor.isBlank() ? ownerName : actor.trim();
        recordingActive = false;
        recordingStoppedAt = clock.instant();
        upsertRecordingSnapshot(false);
        recordActivity("session.recording_stopped", resolvedActor, currentRecordingId);
        recordAudit("session.recording_stopped", resolvedActor, currentRecordingId);
        recordReplay("recording", "Session recording stopped", currentRecordingId);
    }

    synchronized SessionShareTokenDescriptor issueShareToken(String requestedRole) {
        String role = normalizeRole(requestedRole);
        if (!attached) {
            return new SessionShareTokenDescriptor(role, "[unavailable]", sanitizedSessionValue(shareUrl()), expiresAt.toString(), false);
        }
        if ("guest".equals(role) && !allowGuests) {
            return new SessionShareTokenDescriptor(role, "[unavailable]", sanitizedSessionValue(shareUrl()), expiresAt.toString(), false);
        }
        bumpVersion();
        Instant issuedAt = clock.instant();
        Instant tokenExpiresAt = issuedAt.plus(tokenTtl());
        String token = "share_" + UUID.randomUUID();
        issuedShareTokens.put(token, new IssuedShareToken(role, tokenExpiresAt));
        pruneExpiredShareTokens(issuedAt);
        lastIssuedShareRole = role;
        lastIssuedShareTokenPreview = preview(token);
        recordActivity("session.share_issued", ownerName, role);
        recordAudit("session.share_issued", ownerName, role);
        recordReplay("share", "Share token issued", role + " token " + sanitizedTokenPreview(lastIssuedShareTokenPreview));
        return new SessionShareTokenDescriptor(
                role,
                sanitizedTokenPreview(lastIssuedShareTokenPreview),
                shareUrl() + "?token=" + token,
                tokenExpiresAt.toString(),
                true
        );
    }

    synchronized SessionAccessValidationDescriptor validateShareToken(String token) {
        bumpVersion();
        Instant now = clock.instant();
        pruneExpiredShareTokens(now);
        if (token == null || token.isBlank()) {
            recordActivity("session.validation_failed", "unknown", "missing token");
            recordAudit("session.validation_failed", "unknown", "missing token");
            recordReplay("access", "Validation failed", "missing token");
            return new SessionAccessValidationDescriptor(false, "unknown", "Missing token", viewerCount, sessionId);
        }
        IssuedShareToken issuedShareToken = issuedShareTokens.get(token);
        if (issuedShareToken == null) {
            recordActivity("session.validation_failed", "unknown", "invalid token");
            recordAudit("session.validation_failed", "unknown", "invalid token");
            recordReplay("access", "Validation failed", "invalid token");
            return new SessionAccessValidationDescriptor(false, "unknown", "Invalid token", viewerCount, sessionId);
        }
        if (issuedShareToken.expiresAt().isBefore(now)) {
            issuedShareTokens.remove(token);
            recordActivity("session.validation_failed", issuedShareToken.role(), "expired token");
            recordAudit("session.validation_failed", issuedShareToken.role(), "expired token");
            recordReplay("access", "Validation failed", "expired token");
            return new SessionAccessValidationDescriptor(false, issuedShareToken.role(), "Expired token", viewerCount, sessionId);
        }
        if ("guest".equals(issuedShareToken.role()) && !allowGuests) {
            recordActivity("session.validation_failed", issuedShareToken.role(), "guest access disabled");
            recordAudit("session.validation_failed", issuedShareToken.role(), "guest access disabled");
            recordReplay("access", "Validation failed", "guest access disabled");
            return new SessionAccessValidationDescriptor(false, issuedShareToken.role(), "Guest access disabled", viewerCount, sessionId);
        }
        viewerCount += 1;
        recordMemberAccess(token, issuedShareToken.role(), now);
        recordActivity("session.validation_succeeded", issuedShareToken.role(), "access granted");
        recordAudit("session.validation_succeeded", issuedShareToken.role(), "access granted");
        recordReplay("access", "Validation succeeded", issuedShareToken.role() + " joined");
        return new SessionAccessValidationDescriptor(true, issuedShareToken.role(), "Access granted", viewerCount, sessionId);
    }

    synchronized List<SessionReplayEntryDescriptor> replayEntries() {
        return List.copyOf(replay);
    }

    synchronized HostedSessionViewDescriptor hostedView() {
        return hostedView == null ? buildHostedView(null, "unpublished", hostedMembersSnapshot(clock.instant())) : hostedView;
    }

    synchronized List<HostedSessionViewDescriptor> hostedHistory() {
        return List.copyOf(hostedHistory);
    }

    synchronized List<SessionActivityEventDescriptor> collaborationActivity() {
        return List.copyOf(activity);
    }

    synchronized List<SessionDebugNoteDescriptor> collaborationDebugNotes() {
        return List.copyOf(debugNotes);
    }

    synchronized List<SessionRecordingDescriptor> collaborationRecordings() {
        return List.copyOf(recordings);
    }

    synchronized HostedSessionViewDescriptor addHostedMember(String memberId, String role, String source, String actor) {
        if (!attached || hostedView == null || memberId == null || memberId.isBlank()) {
            return hostedView();
        }
        bumpVersion();
        Instant now = clock.instant();
        String resolvedMemberId = memberId.trim();
        HostedMember existing = hostedMembers.get(resolvedMemberId);
        String resolvedActor = actor == null || actor.isBlank() ? resolvedMemberId : actor.trim();
        hostedMembers.put(resolvedMemberId, new HostedMember(
                resolvedMemberId,
                normalizeHostedRole(role),
                source == null || source.isBlank() ? "relay-viewer" : source.trim(),
                existing == null ? now : existing.joinedAt(),
                lastPublishedAt,
                focusedArtifactType,
                focusedArtifactId,
                "hosted viewer joined"
        ));
        hostedView = rebuildHostedView();
        recordActivity("hosted.viewer_joined", resolvedActor, resolvedMemberId);
        recordAudit("hosted.viewer_joined", resolvedActor, resolvedMemberId);
        recordReplay("hosted", "Hosted viewer joined", resolvedMemberId);
        return hostedView;
    }

    synchronized HostedSessionViewDescriptor removeHostedMember(String memberId, String actor) {
        if (!attached || hostedView == null || memberId == null || memberId.isBlank()) {
            return hostedView();
        }
        String resolvedMemberId = memberId.trim();
        if ("owner".equals(resolvedMemberId) || hostedMembers.remove(resolvedMemberId) == null) {
            return hostedView;
        }
        bumpVersion();
        String resolvedActor = actor == null || actor.isBlank() ? ownerName : actor.trim();
        hostedView = rebuildHostedView();
        recordActivity("hosted.viewer_left", resolvedActor, resolvedMemberId);
        recordAudit("hosted.viewer_left", resolvedActor, resolvedMemberId);
        recordReplay("hosted", "Hosted viewer left", resolvedMemberId);
        return hostedView;
    }

    synchronized void markTunnelOpened(RelayTunnelOpenResult result) {
        bumpVersion();
        relayTunnelId = result.tunnelId();
        tunnelStatus = result.tunnelStatus();
        tunnelOpenedAt = parseInstant(result.openedAt());
        tunnelClosedAt = null;
        lastError = null;
        recordActivity("relay.tunnel_opened", ownerName, relayTunnelId);
        recordAudit("relay.tunnel_opened", ownerName, relayTunnelId);
        recordReplay("relay", "Relay tunnel opened", relayTunnelId);
    }

    synchronized void markTunnelStreamConnected() {
        bumpVersion();
        tunnelStatus = "streaming";
        reconnectAt = null;
        lastError = null;
        recordActivity("relay.tunnel_stream_connected", ownerName, relayTunnelId);
        recordReplay("relay", "Tunnel stream connected", relayTunnelId);
    }

    synchronized void markTunnelStreamEvent(String eventName, String payload) {
        bumpVersion();
        if ("heartbeat".equals(eventName)) {
            tunnelStatus = "streaming";
            markHeartbeat(clock.instant());
        } else if ("sync".equals(eventName)) {
            syncStatus = "streaming";
        } else if ("tunnel-closed".equals(eventName)) {
            tunnelStatus = "closed";
            tunnelClosedAt = clock.instant();
            relayTunnelId = null;
        }
        recordActivity("relay.tunnel_event", ownerName, eventName + (payload == null || payload.isBlank() ? "" : " " + payload));
        recordReplay("relay", "Tunnel event", eventName);
    }

    synchronized void markTunnelStreamClosed() {
        bumpVersion();
        if (!"closed".equals(tunnelStatus)) {
            tunnelStatus = "stream-closed";
        }
        relayTunnelId = null;
        reconnectAt = clock.instant().plus(reconnectDelay());
        if (lastError == null) {
            lastError = "Relay tunnel stream closed";
        }
        recordActivity("relay.tunnel_stream_closed", ownerName, relayTunnelId);
        recordReplay("relay", "Tunnel stream closed", relayTunnelId);
    }

    synchronized void markTunnelStreamFailed(String message) {
        bumpVersion();
        tunnelStatus = "stream-failed";
        relayTunnelId = null;
        lastError = message;
        reconnectAt = clock.instant().plus(reconnectDelay());
        recordActivity("relay.tunnel_stream_failed", ownerName, message);
        recordAudit("relay.tunnel_stream_failed", ownerName, message);
        recordReplay("relay", "Tunnel stream failed", message);
    }

    synchronized void markTunnelOpenFailure(String message) {
        bumpVersion();
        relayTunnelId = null;
        tunnelStatus = "open-failed";
        tunnelOpenedAt = null;
        tunnelClosedAt = null;
        lastError = message;
        recordActivity("relay.tunnel_open_failed", ownerName, message);
        recordAudit("relay.tunnel_open_failed", ownerName, message);
        recordReplay("relay", "Relay tunnel open failed", message);
    }

    synchronized void markTunnelClosed(RelayTunnelCloseResult result) {
        bumpVersion();
        relayTunnelId = null;
        tunnelStatus = result.tunnelStatus();
        tunnelClosedAt = parseInstant(result.closedAt());
        recordActivity("relay.tunnel_closed", ownerName, result.tunnelStatus());
        recordAudit("relay.tunnel_closed", ownerName, result.tunnelStatus());
        recordReplay("relay", "Relay tunnel closed", result.tunnelStatus());
    }

    synchronized void recordRequestReplay(CapturedRequest request) {
        if (!attached) {
            return;
        }
        bumpVersion();
        recordReplay(
                "request",
                request.method() + " " + request.path(),
                "status " + request.responseStatus(),
                "request",
                request.requestId()
        );
        refreshActiveRecording();
    }

    synchronized void focusArtifact(String artifactType, String artifactId, String actor) {
        if (!attached) {
            return;
        }
        bumpVersion();
        focusedArtifactType = artifactType;
        focusedArtifactId = artifactId;
        focusedBy = actor == null || actor.isBlank() ? ownerName : actor.trim();
        focusedAt = clock.instant();
        updateMemberAction(focusedBy, "focused " + artifactType + ":" + artifactId);
        recordActivity("session.inspect", focusedBy, artifactType + ":" + artifactId);
        recordAudit("session.inspect", focusedBy, artifactType + ":" + artifactId);
        recordReplay("inspect", "Artifact focused", artifactType + ":" + artifactId, artifactType, artifactId);
        refreshActiveRecording();
    }

    synchronized void addDebugNote(String author, String message, String artifactType, String artifactId) {
        if (!attached || message == null || message.isBlank()) {
            return;
        }
        bumpVersion();
        String resolvedAuthor = author == null || author.isBlank() ? ownerName : author.trim();
        String resolvedArtifactType = artifactType == null || artifactType.isBlank() ? focusedArtifactType : artifactType;
        String resolvedArtifactId = artifactId == null || artifactId.isBlank() ? focusedArtifactId : artifactId;
        debugNotes.add(0, new SessionDebugNoteDescriptor(
                "note-" + UUID.randomUUID(),
                resolvedAuthor,
                message.trim(),
                resolvedArtifactType,
                resolvedArtifactId,
                clock.instant().toString()
        ));
        if (debugNotes.size() > 25) {
            debugNotes.remove(debugNotes.size() - 1);
        }
        updateMemberAction(resolvedAuthor, "debug note");
        recordActivity("session.note_added", resolvedAuthor, message.trim());
        recordAudit("session.note_added", resolvedAuthor, message.trim());
        recordReplay("debug", "Debug note added", message.trim(), resolvedArtifactType, resolvedArtifactId);
        refreshActiveRecording();
    }

    synchronized SessionState state() {
        return new SessionState(
                sessionId,
                ownerName,
                allowGuests ? List.of("owner", "viewer", "guest") : List.of("owner", "viewer"),
                relayUrl(),
                shareUrl(),
                expiresAt.toString(),
                relayConnectionId
        );
    }

    private String relayUrl() {
        String baseUrl = trimTrailingSlash(remoteSettings.getRelayBaseUrl());
        return baseUrl + "/sessions/" + sessionId;
    }

    private String shareUrl() {
        String baseUrl = trimTrailingSlash(remoteSettings.getAppBaseUrl());
        return baseUrl + "/s/" + sessionId;
    }

    private Duration tokenTtl() {
        return Duration.ofMinutes(remoteSettings.getTokenTtlMinutes());
    }

    private Duration heartbeatInterval() {
        return Duration.ofSeconds(remoteSettings.getHeartbeatIntervalSeconds());
    }

    private Duration reconnectDelay() {
        return Duration.ofSeconds(remoteSettings.getReconnectDelaySeconds());
    }

    private int activeShareTokens() {
        pruneExpiredShareTokens(clock.instant());
        return issuedShareTokens.size();
    }

    private List<SessionMemberDescriptor> activeMembers() {
        return activeMembers.values().stream()
                .map(member -> new SessionMemberDescriptor(
                        member.memberId(),
                        member.role(),
                        member.source(),
                        member.joinedAt().toString(),
                        member.lastSeenAt().toString()
                ))
                .toList();
    }

    private List<SessionWorkspaceMemberDescriptor> workspaceMembers() {
        List<SessionWorkspaceMemberDescriptor> members = new ArrayList<>();
        if (attached) {
            members.add(new SessionWorkspaceMemberDescriptor(
                    "owner",
                    "owner",
                    "local-session",
                    toString(lastRotatedAt),
                    latestOwnerTimestamp(),
                    ownerFocusType(),
                    ownerFocusId(),
                    latestOwnerAction()
            ));
        }
        activeMembers.values().forEach(member -> members.add(new SessionWorkspaceMemberDescriptor(
                member.memberId(),
                member.role(),
                member.source(),
                member.joinedAt().toString(),
                member.lastSeenAt().toString(),
                focusedArtifactType,
                focusedArtifactId,
                member.lastAction()
        )));
        return members;
    }

    private int countMembersByRole(String role) {
        return (int) activeMembers.values().stream()
                .filter(member -> role.equals(member.role()))
                .count();
    }

    private List<String> recentActors() {
        return activity.stream()
                .map(SessionActivityEventDescriptor::actor)
                .filter(actor -> actor != null && !actor.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private void pruneExpiredShareTokens(Instant now) {
        issuedShareTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String preview(String token) {
        if (token.length() <= 8) {
            return token;
        }
        return token.substring(0, 6) + "…" + token.substring(token.length() - 4);
    }

    private String sanitizedSessionValue(String value) {
        if (!secretsSettings.isMaskSessionSecrets() && !dataPolicy.maskSessionSecrets()) {
            return value;
        }
        return dataPolicy.sanitizeSessionSecret(value);
    }

    private String sanitizedTokenPreview(String value) {
        if (!secretsSettings.isMaskSessionSecrets() && !dataPolicy.maskSessionSecrets()) {
            return value;
        }
        return value == null || value.isBlank() ? value : dataPolicy.maskValue();
    }

    private String normalizeRole(String requestedRole) {
        if ("guest".equalsIgnoreCase(requestedRole)) {
            return "guest";
        }
        return "viewer";
    }

    private String normalizeHostedRole(String requestedRole) {
        if ("owner".equalsIgnoreCase(requestedRole)) {
            return "owner";
        }
        return normalizeRole(requestedRole);
    }

    private void recordMemberAccess(String token, String role, Instant now) {
        ActiveMember existing = activeMembers.get(token);
        if (existing == null) {
            activeMembers.put(token, new ActiveMember(
                    "member-" + (activeMembers.size() + 1),
                    role,
                    "share-token",
                    now,
                    now,
                    "validated access"
            ));
            return;
        }
        activeMembers.put(token, new ActiveMember(
                existing.memberId(),
                existing.role(),
                existing.source(),
                existing.joinedAt(),
                now,
                existing.lastAction()
        ));
    }

    private void updateMemberAction(String actor, String action) {
        if (actor == null || actor.isBlank()) {
            return;
        }
        activeMembers.replaceAll((token, member) -> member.memberId().equals(actor) || actor.equals(member.role())
                ? new ActiveMember(
                member.memberId(),
                member.role(),
                member.source(),
                member.joinedAt(),
                clock.instant(),
                action
        ) : member);
    }

    private void recordActivity(String eventType, String actor, String detail) {
        activity.add(0, new SessionActivityEventDescriptor(
                eventType,
                actor,
                detail,
                clock.instant().toString()
        ));
        trimList(activity, remoteSettings.getSessionActivityLimit());
        refreshActiveRecording();
    }

    private void recordReplay(String category, String title, String payloadPreview) {
        recordReplay(category, title, payloadPreview, null, null);
    }

    private void recordReplay(String category, String title, String payloadPreview, String artifactType, String artifactId) {
        replay.add(0, new SessionReplayEntryDescriptor(
                "replay-" + UUID.randomUUID(),
                category,
                title,
                payloadPreview,
                artifactType,
                artifactId,
                clock.instant().toString()
        ));
        trimList(replay, remoteSettings.getSessionReplayLimit());
        refreshActiveRecording();
    }

    private void recordAudit(String eventType, String actor, String detail) {
        audit.add(0, new SessionAuditEventDescriptor(
                "audit-" + UUID.randomUUID(),
                eventType,
                actor,
                detail,
                clock.instant().toString(),
                null,
                sessionId
        ));
        trimList(audit, remoteSettings.getSessionAuditLimit());
    }

    private void refreshActiveRecording() {
        if (recordingActive) {
            upsertRecordingSnapshot(true);
        }
    }

    private void upsertRecordingSnapshot(boolean active) {
        if (currentRecordingId == null || recordingStartedAt == null) {
            return;
        }
        SessionRecordingDescriptor snapshot = new SessionRecordingDescriptor(
                currentRecordingId,
                recordingStartedBy == null ? ownerName : recordingStartedBy,
                recordingStartedAt.toString(),
                active ? null : toString(recordingStoppedAt),
                active,
                activity.size(),
                replay.size(),
                debugNotes.size(),
                activeMembers.size(),
                focusedArtifactType,
                focusedArtifactId,
                recordingHighlights()
        );
        recordings.removeIf(recording -> recording.recordingId().equals(currentRecordingId));
        recordings.add(0, snapshot);
        trimList(recordings, remoteSettings.getSessionRecordingLimit());
    }

    private List<String> recordingHighlights() {
        List<String> highlights = new ArrayList<>();
        if (!activity.isEmpty()) {
            highlights.add(activity.get(0).eventType());
        }
        if (!replay.isEmpty()) {
            highlights.add(replay.get(0).title());
        }
        if (!debugNotes.isEmpty()) {
            highlights.add("note:" + debugNotes.get(0).message());
        }
        if (focusedArtifactId != null && focusedArtifactType != null) {
            highlights.add("focus:" + focusedArtifactType + ":" + focusedArtifactId);
        }
        return highlights.stream().limit(4).toList();
    }

    private void markHeartbeat(Instant now) {
        lastHeartbeatAt = now;
        nextHeartbeatAt = now.plus(heartbeatInterval());
    }

    private void bumpVersion() {
        sessionVersion += 1;
    }

    private HostedSessionViewDescriptor rebuildHostedView() {
        return buildHostedView(
                hostedView == null ? null : hostedView.viewId(),
                hostedView == null ? "published" : hostedView.durableState(),
                hostedMembers.values().stream()
                        .map(member -> new HostedSessionMemberDescriptor(
                                member.memberId(),
                                member.role(),
                                member.source(),
                                toString(member.joinedAt()),
                                toString(member.publishedAt()),
                                member.focusedArtifactType(),
                                member.focusedArtifactId(),
                                member.lastAction()
                        ))
                        .toList()
        );
    }

    private HostedSessionViewDescriptor buildHostedView(String viewId, String durableState, List<HostedSessionMemberDescriptor> members) {
        List<String> replayTitles = replay.stream()
                .map(SessionReplayEntryDescriptor::title)
                .limit(5)
                .toList();
        return new HostedSessionViewDescriptor(
                sessionId,
                viewId == null ? "view-" + sessionId : viewId,
                lastSyncId,
                publishedSessionVersion != null,
                durableState,
                sessionVersion,
                publishedSessionVersion,
                relayViewerUrl,
                ownerName,
                allowGuests ? "owner-viewer-guest" : "owner-viewer",
                activeMembers.size(),
                replay.size(),
                debugNotes.size(),
                recordings.size(),
                focusedArtifactType,
                focusedArtifactId,
                toString(lastPublishedAt),
                List.copyOf(members),
                recentActors(),
                replayTitles
        );
    }

    private List<HostedSessionMemberDescriptor> hostedMembersSnapshot(Instant publishedAt) {
        List<HostedSessionMemberDescriptor> members = new ArrayList<>();
        if (attached) {
            members.add(new HostedSessionMemberDescriptor(
                    "owner",
                    "owner",
                    "local-session",
                    toString(lastRotatedAt),
                    toString(publishedAt),
                    ownerFocusType(),
                    ownerFocusId(),
                    latestOwnerAction()
            ));
        }
        activeMembers.values().forEach(member -> members.add(new HostedSessionMemberDescriptor(
                member.memberId(),
                member.role(),
                member.source(),
                member.joinedAt().toString(),
                toString(publishedAt),
                focusedArtifactType,
                focusedArtifactId,
                member.lastAction()
        )));
        return List.copyOf(members);
    }

    private String toString(Instant value) {
        return value == null ? null : value.toString();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private <T> void trimList(List<T> list, int limit) {
        if (limit == 0) {
            list.clear();
            return;
        }
        while (list.size() > limit) {
            list.remove(list.size() - 1);
        }
    }

    private String latestOwnerTimestamp() {
        return activity.stream()
                .filter(event -> ownerName.equals(event.actor()))
                .map(SessionActivityEventDescriptor::timestamp)
                .findFirst()
                .orElse(toString(lastRotatedAt));
    }

    private String latestOwnerAction() {
        return activity.stream()
                .filter(event -> ownerName.equals(event.actor()))
                .map(SessionActivityEventDescriptor::eventType)
                .findFirst()
                .orElse(attached ? "session.attached" : "idle");
    }

    private String ownerFocusType() {
        return ownerName.equals(focusedBy) ? focusedArtifactType : null;
    }

    private String ownerFocusId() {
        return ownerName.equals(focusedBy) ? focusedArtifactId : null;
    }

    record SessionState(
            String sessionId,
            String ownerName,
            List<String> allowedRoles,
            String relayUrl,
            String shareUrl,
            String expiresAt,
            String connectionId
    ) {
    }

    private record IssuedShareToken(
            String role,
            Instant expiresAt
    ) {
    }

    private record ActiveMember(
            String memberId,
            String role,
            String source,
            Instant joinedAt,
            Instant lastSeenAt,
            String lastAction
    ) {
    }

    private record HostedMember(
            String memberId,
            String role,
            String source,
            Instant joinedAt,
            Instant publishedAt,
            String focusedArtifactType,
            String focusedArtifactId,
            String lastAction
    ) {
    }
}
