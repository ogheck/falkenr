package com.devtools.ui.relay;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import com.devtools.ui.core.model.RelayAccountDescriptor;
import com.devtools.ui.core.model.RelayOrganizationDescriptor;
import com.devtools.ui.core.model.RemoteSessionDescriptor;
import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;

import java.util.List;

record RelayAttachPayload(
        String sessionId,
        String ownerName,
        List<String> allowedRoles,
        String relayUrl,
        String shareUrl,
        String encryptedToken,
        String expiresAt
) {
}

record RelayAttachResponse(
        String handshakeId,
        String connectionId,
        String relayStatus,
        String leaseId,
        String leaseExpiresAt,
        String viewerUrl,
        String organizationId,
        String organizationName,
        String ownerAccountId
) {
}

record RelayOwnerTransferRequest(
        String connectionId,
        String targetViewerSessionId
) {
}

record RelayHeartbeatPayload(
        String sessionId,
        String connectionId
) {
}

record RelayHeartbeatResponse(
        String relayStatus,
        String tunnelStatus,
        boolean connected,
        String leaseId,
        String leaseExpiresAt
) {
}

record RelaySyncPayload(
        String sessionId,
        String connectionId,
        RemoteSessionDescriptor snapshot
) {
}

record RelaySyncResponse(
        String syncId,
        String viewId,
        String relayStatus,
        boolean accepted
) {
}

record RelayTunnelOpenPayload(
        String sessionId,
        String connectionId
) {
}

record RelayTunnelOpenResponse(
        String tunnelId,
        String tunnelStatus,
        String openedAt,
        String streamUrl
) {
}

record RelayTunnelClosePayload(
        String sessionId,
        String connectionId,
        String tunnelId
) {
}

record RelayTunnelCloseResponse(
        String tunnelStatus,
        String closedAt
) {
}

record RelayAccessTokenPayload(
        String sessionId,
        String token,
        String role,
        String expiresAt
) {
}

record RelayViewerSessionRequest(
        String token,
        String accountSessionToken,
        String viewerName
) {
}

record RelayViewerSessionResponse(
        String viewerSessionId,
        String viewerSessionToken,
        String role,
        String expiresAt
) {
}

record RelayRequestArtifactPayload(
        String sessionId,
        CapturedRequest request
) {
}

record RelaySessionCollaborationPayload(
        String sessionId,
        List<SessionActivityEventDescriptor> activity,
        List<SessionDebugNoteDescriptor> debugNotes,
        List<SessionRecordingDescriptor> recordings
) {
}

record RelayPagedResponse<T>(
        List<T> items,
        int total,
        int offset,
        int limit
) {
}

record RelayServerStatusResponse(
        String status,
        String publicBaseUrl,
        String persistenceFile,
        boolean persistenceFileExists,
        int sessionCount,
        int organizationCount,
        int accountCount,
        int attachedSessionCount,
        int viewerSessionCount,
        int tunnelCount
) {
}

record RelayOrganizationRequest(
        String organizationId,
        String organizationName
) {
}

record RelayAccountRequest(
        String accountId,
        String displayName,
        String organizationId,
        String role
) {
}

record RelayAccountLoginRequest(
        String accountId,
        String displayName,
        String organizationId
) {
}

record RelayAccountLoginResponse(
        String accountSessionToken,
        String expiresAt,
        RelayAccountDescriptor account
) {
}

record RelayInvitationRequest(
        String connectionId,
        String email,
        String displayName,
        String role,
        String expiresAt
) {
}

record RelayInvitationResponse(
        String invitationId,
        String invitationToken,
        String organizationId,
        String email,
        String displayName,
        String role,
        String expiresAt,
        boolean accepted
) {
}

record RelayInvitationAcceptRequest(
        String invitationToken,
        String accountId,
        String displayName
) {
}

record RelayEntitlementRequest(
        String plan,
        String status,
        Integer seatLimit
) {
}

record RelayEntitlementResponse(
        String organizationId,
        String plan,
        String status,
        int seatLimit,
        boolean teamEnabled
) {
}

record RelayQuotaResponse(
        String organizationId,
        int maxSessions,
        int sessionCount,
        int maxProjects,
        int projectCount,
        int maxViewerSessions,
        int viewerSessionCount,
        int maxRequestArtifactsPerSession,
        int maxRequestArtifacts,
        int requestArtifactCount
) {
}

record RelayHostedSessionSummary(
        String sessionId,
        String ownerName,
        String projectId,
        String projectName,
        String relayStatus,
        String tunnelStatus,
        String accessScope,
        String viewerUrl,
        boolean published,
        String lastPublishedAt,
        long sessionVersion,
        int activeMemberCount,
        int viewerSessionCount,
        int replayCount,
        int debugNoteCount,
        int recordingCount
) {
}

record RelayProjectDescriptor(
        String projectId,
        String organizationId,
        String projectName,
        String createdAt,
        String updatedAt
) {
}

record RelayProjectUpsertRequest(
        String projectId,
        String projectName
) {
}

record RelaySessionProjectAssignRequest(
        String projectId
) {
}

record RelayHostedDashboardResponse(
        RelayAccountDescriptor account,
        RelayOrganizationDescriptor organization,
        RelayEntitlementResponse entitlement,
        RelayQuotaResponse quota,
        RelayServerStatusResponse status,
        List<RelayHostedSessionSummary> sessions,
        List<RelayProjectDescriptor> projects,
        List<RelayAccountDescriptor> organizationAccounts
) {
}

record RelayUsageAnalyticsResponse(
        String organizationId,
        int sessionCount,
        int publishedSessionCount,
        int attachedSessionCount,
        int activeViewerSessionCount,
        int requestArtifactCount,
        int replayEntryCount,
        int debugNoteCount,
        int recordingCount,
        int auditEventCount,
        List<String> topActors,
        List<String> recentReplayTitles
) {
}

record RelayCloudRequestReplayResponse(
        String sessionId,
        String requestId,
        String method,
        String path,
        int originalStatus,
        String capturedAt,
        boolean replayableBody,
        String bodyPreview,
        String curlCommand,
        String replayHint
) {
}

record RelayDebugRequestSummary(
        String requestId,
        String method,
        String path,
        int responseStatus,
        String capturedAt,
        boolean binaryBody,
        boolean bodyTruncated
) {
}

record RelayRemoteDebugResponse(
        String sessionId,
        String organizationId,
        String ownerName,
        String relayStatus,
        String tunnelStatus,
        String focusedArtifactType,
        String focusedArtifactId,
        int activityCount,
        int debugNoteCount,
        int recordingCount,
        int requestArtifactCount,
        List<SessionActivityEventDescriptor> recentActivity,
        List<SessionDebugNoteDescriptor> recentDebugNotes,
        List<SessionRecordingDescriptor> recentRecordings,
        List<RelayDebugRequestSummary> recentRequests
) {
}

record RelayDashboardAccountRequest(
        String accountId,
        String displayName,
        String role
) {
}

record RelayAdminImportResponse(
        String status,
        int sessionCount,
        int organizationCount,
        int accountCount
) {
}

record RelayDirectoryResponse(
        List<RelayOrganizationDescriptor> organizations,
        List<RelayAccountDescriptor> accounts
) {
}
