package com.devtools.ui.relay;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import com.devtools.ui.core.model.RelayAccountDescriptor;
import com.devtools.ui.core.model.RelayOrganizationDescriptor;
import com.devtools.ui.core.model.RelaySessionIdentityDescriptor;
import com.devtools.ui.core.model.RelayViewerSessionDescriptor;
import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionAuditEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/sessions")
class RelaySessionController {

    private final InMemoryRelaySessionStore sessionStore;

    RelaySessionController(InMemoryRelaySessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    private String resolveAccountSessionToken(String accountSessionToken, HttpServletRequest request) {
        if (accountSessionToken != null && !accountSessionToken.isBlank()) {
            return accountSessionToken.trim();
        }
        if (request == null) {
            throw new RelayAuthException("Missing relay account session");
        }
        Object sessionToken = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(RelayWebLoginSuccessHandler.ACCOUNT_SESSION_ATTR);
        if (sessionToken instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new RelayAuthException("Missing relay account session");
    }

    @GetMapping("/status")
    RelayServerStatusResponse status() {
        return sessionStore.status();
    }

    @GetMapping("/admin/export")
    Object exportState() {
        return sessionStore.exportState();
    }

    @GetMapping("/admin/audit")
    RelayPagedResponse<SessionAuditEventDescriptor> adminAudit(@RequestParam(name = "organizationId", required = false) String organizationId,
                                                               @RequestParam(name = "sessionId", required = false) String sessionId) {
        List<SessionAuditEventDescriptor> items = sessionStore.auditEvents(organizationId, sessionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }

    @PostMapping("/admin/import")
    RelayAdminImportResponse importState(@RequestBody Object snapshot) {
        return sessionStore.importState(snapshot);
    }

    @GetMapping("/directory")
    RelayDirectoryResponse directory(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                     HttpServletRequest request) {
        return sessionStore.scopedDirectory(resolveAccountSessionToken(accountSessionToken, request));
    }

    @PostMapping("/directory/organizations")
    RelayOrganizationDescriptor upsertOrganization(@RequestBody RelayOrganizationRequest request) {
        throw new UnsupportedOperationException("Organization creation is not exposed via the public directory API.");
    }

    @GetMapping("/directory/organizations/{organizationId}/entitlement")
    RelayEntitlementResponse entitlement(@PathVariable String organizationId,
                                         @RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                         HttpServletRequest request) {
        return sessionStore.directoryEntitlement(resolveAccountSessionToken(accountSessionToken, request), organizationId);
    }

    @PostMapping("/directory/organizations/{organizationId}/entitlement")
    RelayEntitlementResponse upsertEntitlement(@PathVariable String organizationId,
                                               @RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                               HttpServletRequest httpRequest,
                                               @RequestBody RelayEntitlementRequest payload) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("Missing organizationId");
        }
        // Enforced inside the sessionStore (caller org must match).
        return sessionStore.directoryUpsertEntitlement(resolveAccountSessionToken(accountSessionToken, httpRequest), payload);
    }

    @PostMapping("/directory/accounts")
    RelayAccountDescriptor upsertAccount(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                         HttpServletRequest httpRequest,
                                         @RequestBody RelayAccountRequest payload) {
        return sessionStore.directoryUpsertAccount(resolveAccountSessionToken(accountSessionToken, httpRequest), payload);
    }

    @PostMapping("/accounts/login")
    RelayAccountLoginResponse loginAccount(@RequestBody RelayAccountLoginRequest request) {
        return sessionStore.loginAccount(request);
    }

    @PostMapping("/accounts/rotate")
    RelayAccountLoginResponse rotateAccountSession(@RequestParam("accountSession") String accountSessionToken) {
        return sessionStore.rotateAccountSession(accountSessionToken);
    }

    @GetMapping("/dashboard")
    RelayHostedDashboardResponse dashboard(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                           HttpServletRequest request) {
        return sessionStore.dashboard(resolveAccountSessionToken(accountSessionToken, request));
    }

    @GetMapping("/dashboard/analytics")
    RelayUsageAnalyticsResponse usageAnalytics(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                               HttpServletRequest request) {
        return sessionStore.usageAnalytics(resolveAccountSessionToken(accountSessionToken, request));
    }

    @GetMapping("/dashboard/projects")
    List<RelayProjectDescriptor> dashboardProjects(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                   HttpServletRequest request) {
        return sessionStore.dashboardProjects(resolveAccountSessionToken(accountSessionToken, request));
    }

    @PostMapping("/dashboard/projects")
    RelayProjectDescriptor dashboardUpsertProject(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                  HttpServletRequest httpRequest,
                                                  @RequestBody RelayProjectUpsertRequest payload) {
        return sessionStore.dashboardUpsertProject(resolveAccountSessionToken(accountSessionToken, httpRequest), payload);
    }

    @PostMapping("/dashboard/sessions/{sessionId}/project")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dashboardAssignSessionProject(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                       HttpServletRequest httpRequest,
                                       @PathVariable String sessionId,
                                       @RequestBody RelaySessionProjectAssignRequest payload) {
        sessionStore.dashboardAssignSessionProject(resolveAccountSessionToken(accountSessionToken, httpRequest), sessionId, payload);
    }

    @PostMapping("/dashboard/accounts")
    RelayAccountDescriptor dashboardUpsertAccount(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                  HttpServletRequest httpRequest,
                                                  @RequestBody RelayDashboardAccountRequest payload) {
        return sessionStore.dashboardUpsertAccount(resolveAccountSessionToken(accountSessionToken, httpRequest), payload);
    }

    @DeleteMapping("/dashboard/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dashboardRemoveAccount(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                HttpServletRequest httpRequest,
                                @PathVariable String accountId) {
        sessionStore.dashboardRemoveAccount(resolveAccountSessionToken(accountSessionToken, httpRequest), accountId);
    }

    @PatchMapping("/dashboard/accounts/{accountId}/role")
    RelayAccountDescriptor dashboardUpdateAccountRole(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                      HttpServletRequest httpRequest,
                                                      @PathVariable String accountId,
                                                      @RequestBody RelayAccountRoleUpdateRequest payload) {
        return sessionStore.dashboardUpdateAccountRole(resolveAccountSessionToken(accountSessionToken, httpRequest), accountId, payload);
    }

    @PostMapping("/dashboard/entitlement")
    RelayEntitlementResponse dashboardUpdateEntitlement(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                        HttpServletRequest httpRequest,
                                                        @RequestBody RelayEntitlementRequest payload) {
        return sessionStore.dashboardUpdateEntitlement(resolveAccountSessionToken(accountSessionToken, httpRequest), payload);
    }

    @PostMapping("/dashboard/sessions/{sessionId}/requests/{requestId}/replay")
    RelayCloudRequestReplayResponse cloudRequestReplay(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                       HttpServletRequest httpRequest,
                                                       @PathVariable String sessionId,
                                                       @PathVariable String requestId) {
        return sessionStore.cloudRequestReplay(resolveAccountSessionToken(accountSessionToken, httpRequest), sessionId, requestId);
    }

    @GetMapping("/dashboard/sessions/{sessionId}/remote-debug")
    RelayRemoteDebugResponse remoteDebugContext(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                HttpServletRequest httpRequest,
                                                @PathVariable String sessionId) {
        return sessionStore.remoteDebugContext(resolveAccountSessionToken(accountSessionToken, httpRequest), sessionId);
    }

    @PostMapping("/dashboard/sessions/{sessionId}/invitations")
    RelayInvitationResponse dashboardCreateInvitation(@RequestParam(name = "accountSession", required = false) String accountSessionToken,
                                                      HttpServletRequest httpRequest,
                                                      @PathVariable String sessionId,
                                                      @RequestBody RelayInvitationRequest payload) {
        return sessionStore.dashboardCreateInvitation(resolveAccountSessionToken(accountSessionToken, httpRequest), sessionId, payload);
    }

    @PostMapping("/{sessionId}/invitations")
    RelayInvitationResponse createInvitation(@PathVariable String sessionId,
                                             @RequestBody RelayInvitationRequest request) {
        return sessionStore.createInvitation(sessionId, request);
    }

    @PostMapping("/invitations/accept")
    RelayAccountLoginResponse acceptInvitation(@RequestBody RelayInvitationAcceptRequest request) {
        return sessionStore.acceptInvitation(request);
    }

    @DeleteMapping("/directory/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeAccount(@PathVariable String accountId,
                       @RequestParam(name = "accountSession", required = false) String accountSessionToken,
                       HttpServletRequest httpRequest) {
        sessionStore.directoryRemoveAccount(resolveAccountSessionToken(accountSessionToken, httpRequest), accountId);
    }

    @PostMapping("/attach")
    RelayAttachResponse attach(@RequestBody RelayAttachPayload payload) {
        return sessionStore.attach(payload);
    }

    @PostMapping("/heartbeat")
    RelayHeartbeatResponse heartbeat(@RequestBody RelayHeartbeatPayload payload) {
        return sessionStore.heartbeat(payload);
    }

    @PostMapping("/sync")
    RelaySyncResponse sync(@RequestBody RelaySyncPayload payload) {
        return sessionStore.sync(payload);
    }

    @PostMapping("/tunnel/open")
    RelayTunnelOpenResponse openTunnel(@RequestBody RelayTunnelOpenPayload payload) {
        return sessionStore.openTunnel(payload);
    }

    @PostMapping("/tunnel/close")
    RelayTunnelCloseResponse closeTunnel(@RequestBody RelayTunnelClosePayload payload) {
        return sessionStore.closeTunnel(payload);
    }

    @GetMapping(path = "/tunnel/{tunnelId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter streamTunnel(@PathVariable String tunnelId,
                            @RequestParam("connectionId") String connectionId) {
        return sessionStore.openTunnelStream(tunnelId, connectionId);
    }

    @PostMapping("/access-tokens/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void registerAccessToken(@RequestBody RelayAccessTokenPayload payload) {
        sessionStore.registerAccessToken(payload);
    }

    @PostMapping("/viewer/{sessionId}/session")
    RelayViewerSessionResponse createViewerSession(@PathVariable String sessionId,
                                                   @RequestBody RelayViewerSessionRequest request) {
        return sessionStore.createViewerSession(sessionId, request);
    }

    @GetMapping("/{sessionId}/viewer-sessions")
    RelayPagedResponse<RelayViewerSessionDescriptor> viewerSessions(@PathVariable String sessionId,
                                                                    @RequestParam("connectionId") String connectionId) {
        List<RelayViewerSessionDescriptor> items = sessionStore.viewerSessions(sessionId, connectionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }

    @DeleteMapping("/{sessionId}/viewer-sessions/{viewerSessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeViewerSession(@PathVariable String sessionId,
                             @PathVariable String viewerSessionId,
                             @RequestParam("connectionId") String connectionId) {
        sessionStore.revokeViewerSession(sessionId, connectionId, viewerSessionId);
    }

    @GetMapping("/{sessionId}/identity")
    RelaySessionIdentityDescriptor sessionIdentity(@PathVariable String sessionId,
                                                   @RequestParam("connectionId") String connectionId) {
        return sessionStore.sessionIdentity(sessionId, connectionId);
    }

    @PostMapping("/{sessionId}/owner/transfer")
    RelaySessionIdentityDescriptor transferOwner(@PathVariable String sessionId,
                                                 @RequestBody RelayOwnerTransferRequest request) {
        return sessionStore.transferOwner(sessionId, request);
    }

    @PostMapping("/artifacts/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void storeRequestArtifact(@RequestBody RelayRequestArtifactPayload payload) {
        sessionStore.storeRequestArtifact(payload);
    }

    @PostMapping("/collaboration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void storeCollaboration(@RequestBody RelaySessionCollaborationPayload payload) {
        sessionStore.storeCollaboration(payload);
    }

    @GetMapping("/{sessionId}/hosted-view")
    HostedSessionViewDescriptor currentHostedView(@PathVariable String sessionId) {
        return sessionStore.currentHostedView(sessionId);
    }

    @GetMapping("/{sessionId}/hosted-history")
    RelayPagedResponse<HostedSessionViewDescriptor> hostedHistory(@PathVariable String sessionId) {
        List<HostedSessionViewDescriptor> items = sessionStore.hostedHistory(sessionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }

    @PostMapping("/{sessionId}/hosted-view/published")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void storePublishedView(@PathVariable String sessionId, @RequestBody HostedSessionViewDescriptor view) {
        sessionStore.storePublishedView(sessionId, view);
    }

    @PostMapping("/{sessionId}/hosted-view/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void storeCurrentView(@PathVariable String sessionId, @RequestBody HostedSessionViewDescriptor view) {
        sessionStore.storeCurrentView(sessionId, view);
    }

    @DeleteMapping("/{sessionId}/hosted-view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearHostedView(@PathVariable String sessionId) {
        sessionStore.clearHostedView(sessionId);
    }

    @GetMapping("/viewer/{sessionId}/hosted-view")
    HostedSessionViewDescriptor viewerHostedView(@PathVariable String sessionId,
                                                 @RequestParam("viewerSession") String viewerSessionId) {
        sessionStore.validateViewerSession(sessionId, viewerSessionId);
        return sessionStore.currentHostedView(sessionId);
    }

    @GetMapping("/viewer/{sessionId}/hosted-history")
    RelayPagedResponse<HostedSessionViewDescriptor> viewerHostedHistory(@PathVariable String sessionId,
                                                                        @RequestParam("viewerSession") String viewerSessionId) {
        sessionStore.validateViewerSession(sessionId, viewerSessionId);
        List<HostedSessionViewDescriptor> items = sessionStore.hostedHistory(sessionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }

    @GetMapping("/viewer/{sessionId}/artifacts/request")
    CapturedRequest viewerRequestArtifact(@PathVariable String sessionId,
                                          @RequestParam("requestId") String requestId,
                                          @RequestParam("viewerSession") String viewerSessionId) {
        sessionStore.validateViewerSession(sessionId, viewerSessionId);
        return sessionStore.requestArtifact(sessionId, requestId);
    }

    @GetMapping("/viewer/{sessionId}/collaboration/activity")
    RelayPagedResponse<SessionActivityEventDescriptor> viewerCollaborationActivity(@PathVariable String sessionId,
                                                                                   @RequestParam("viewerSession") String viewerSessionId) {
        sessionStore.validateViewerSession(sessionId, viewerSessionId);
        List<SessionActivityEventDescriptor> items = sessionStore.collaborationActivity(sessionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }

    @GetMapping("/{sessionId}/audit")
    RelayPagedResponse<SessionAuditEventDescriptor> sessionAudit(@PathVariable String sessionId,
                                                                 @RequestParam("connectionId") String connectionId) {
        List<SessionAuditEventDescriptor> items = sessionStore.sessionAudit(sessionId, connectionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }

    @GetMapping("/viewer/{sessionId}/collaboration/notes")
    RelayPagedResponse<SessionDebugNoteDescriptor> viewerCollaborationNotes(@PathVariable String sessionId,
                                                                            @RequestParam("viewerSession") String viewerSessionId) {
        sessionStore.validateViewerSession(sessionId, viewerSessionId);
        List<SessionDebugNoteDescriptor> items = sessionStore.collaborationDebugNotes(sessionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }

    @GetMapping("/viewer/{sessionId}/collaboration/recordings")
    RelayPagedResponse<SessionRecordingDescriptor> viewerCollaborationRecordings(@PathVariable String sessionId,
                                                                                 @RequestParam("viewerSession") String viewerSessionId) {
        sessionStore.validateViewerSession(sessionId, viewerSessionId);
        List<SessionRecordingDescriptor> items = sessionStore.collaborationRecordings(sessionId);
        return new RelayPagedResponse<>(items, items.size(), 0, items.size());
    }
}
