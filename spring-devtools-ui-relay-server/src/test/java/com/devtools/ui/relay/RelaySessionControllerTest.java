package com.devtools.ui.relay;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.model.HostedSessionMemberDescriptor;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.devtools.ui.relay-server.persistence-file=${java.io.tmpdir}/spring-devtools-ui-relay-server-test-state-${random.uuid}.json",
        "spring.devtools.ui.relay-server.auth-secret=test-relay-secret",
        "spring.devtools.ui.relay-server.auth-issuer=test-relay-issuer"
})
@AutoConfigureMockMvc
class RelaySessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void attachHeartbeatTunnelAndHostedViewFlowWork() throws Exception {
        mockMvc.perform(get("/sessions/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.publicBaseUrl").value("http://localhost:8091"));

        RelayAttachPayload attachPayload = new RelayAttachPayload(
                "session-123",
                "owner",
                List.of("owner", "viewer"),
                "wss://relay.example.test",
                "https://app.example.test/s/session-123",
                "encrypted-token",
                "2030-01-01T00:00:00Z"
        );

        String attachResponse = mockMvc.perform(post("/sessions/attach")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(attachPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handshakeId").isNotEmpty())
                .andExpect(jsonPath("$.connectionId").isNotEmpty())
                .andExpect(jsonPath("$.viewerUrl").value("http://localhost:8091/view/session-123"))
                .andExpect(jsonPath("$.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.organizationName").value("owner Workspace"))
                .andExpect(jsonPath("$.ownerAccountId").value("acct-owner"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayAttachResponse attached = objectMapper.readValue(attachResponse, RelayAttachResponse.class);

        mockMvc.perform(post("/sessions/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RelayHeartbeatPayload("session-123", attached.connectionId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.leaseId").value(attached.leaseId()));

        String tunnelResponse = mockMvc.perform(post("/sessions/tunnel/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RelayTunnelOpenPayload("session-123", attached.connectionId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tunnelStatus").value("open"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayTunnelOpenResponse opened = objectMapper.readValue(tunnelResponse, RelayTunnelOpenResponse.class);
        org.assertj.core.api.Assertions.assertThat(opened.streamUrl())
                .isEqualTo("http://localhost:8091/sessions/tunnel/%s/stream?connectionId=%s".formatted(opened.tunnelId(), attached.connectionId()));

        HostedSessionViewDescriptor view = new HostedSessionViewDescriptor(
                "session-123",
                "view-1",
                "sync-1",
                true,
                "relay-backed",
                3L,
                3L,
                null,
                "owner",
                "team",
                1,
                2,
                1,
                1,
                "request",
                "request-9",
                "2026-04-08T00:00:00Z",
                List.of(new HostedSessionMemberDescriptor(
                        "member-1",
                        "viewer",
                        "share-link",
                        "2026-04-08T00:00:00Z",
                        "2026-04-08T00:00:00Z",
                        "request",
                        "request-9",
                        "inspect"
                )),
                List.of("owner"),
                List.of("POST /users")
        );

        mockMvc.perform(post("/sessions/session-123/hosted-view/current")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(view)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/sessions/session-123/hosted-view/published")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(view)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/sessions/session-123/hosted-view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relayViewerUrl").value("http://localhost:8091/view/session-123"))
                .andExpect(jsonPath("$.members[0].memberId").value("member-1"));

        mockMvc.perform(get("/s/session-123"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/sessions/access-tokens/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RelayAccessTokenPayload(
                                "session-123",
                                "viewer-token-123",
                                "viewer",
                                "2030-01-01T00:00:00Z"
                        ))))
                .andExpect(status().isNoContent());

        String viewerSessionResponse = mockMvc.perform(post("/sessions/viewer/session-123/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "viewer-token-123",
                                  "viewerName": "qa-viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerSessionId").isNotEmpty())
                .andExpect(jsonPath("$.viewerSessionToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("viewer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayViewerSessionResponse viewerSession = objectMapper.readValue(viewerSessionResponse, RelayViewerSessionResponse.class);

        mockMvc.perform(get("/sessions/session-123/viewer-sessions")
                        .queryParam("connectionId", attached.connectionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].viewerSessionId").value(viewerSession.viewerSessionId()))
                .andExpect(jsonPath("$.items[0].viewerName").value("qa-viewer"));

        mockMvc.perform(get("/sessions/session-123/identity")
                        .queryParam("connectionId", attached.connectionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.owner.accountId").value("acct-owner"));

        String accountLoginResponse = mockMvc.perform(post("/sessions/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-owner",
                                  "organizationId": "org-owner"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountSessionToken").isNotEmpty())
                .andExpect(jsonPath("$.account.accountId").value("acct-owner"))
                .andExpect(jsonPath("$.account.organizationId").value("org-owner"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayAccountLoginResponse accountLogin = objectMapper.readValue(accountLoginResponse, RelayAccountLoginResponse.class);
        String rotatedLoginResponse = mockMvc.perform(post("/sessions/accounts/rotate")
                        .queryParam("accountSession", accountLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountSessionToken").isNotEmpty())
                .andExpect(jsonPath("$.account.accountId").value("acct-owner"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        RelayAccountLoginResponse rotatedLogin = objectMapper.readValue(rotatedLoginResponse, RelayAccountLoginResponse.class);

        mockMvc.perform(get("/sessions/directory")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizations[0].organizationId").value("org-owner"))
                .andExpect(jsonPath("$.accounts[0].accountId").value("acct-owner"));

        mockMvc.perform(post("/sessions/directory/accounts")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-alpha-member",
                                  "displayName": "Alpha Member",
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-alpha-member"))
                .andExpect(jsonPath("$.organizationId").value("org-owner"));

        mockMvc.perform(delete("/sessions/directory/accounts/{accountId}", "acct-alpha-member")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/sessions/directory/accounts/{accountId}", "acct-owner")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/sessions/dashboard").queryParam("accountSession", accountLogin.accountSessionToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        mockMvc.perform(get("/sessions/dashboard").queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isOk());

        String accountViewerSessionResponse = mockMvc.perform(post("/sessions/viewer/session-123/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountSessionToken": "%s"
                                }
                                """.formatted(rotatedLogin.accountSessionToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerSessionId").isNotEmpty())
                .andExpect(jsonPath("$.viewerSessionToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("owner"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayViewerSessionResponse accountViewerSession = objectMapper.readValue(accountViewerSessionResponse, RelayViewerSessionResponse.class);

        mockMvc.perform(get("/sessions/viewer/session-123/hosted-view")
                        .queryParam("viewerSession", accountViewerSession.viewerSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("owner"));

        mockMvc.perform(post("/sessions/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-owner",
                                  "organizationId": "org-alpha-team"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/sessions/directory/organizations/{organizationId}/entitlement", "org-owner")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("alpha-trial"))
                .andExpect(jsonPath("$.teamEnabled").value(true));

        mockMvc.perform(post("/sessions/directory/organizations/{organizationId}/entitlement", "org-owner")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plan": "free",
                                  "status": "inactive",
                                  "seatLimit": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamEnabled").value(false));

        mockMvc.perform(post("/sessions/session-123/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectionId": "%s",
                                  "email": "blocked@example.test",
                                  "role": "viewer"
                                }
                                """.formatted(attached.connectionId())))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("plan_required"));

        mockMvc.perform(post("/sessions/directory/organizations/{organizationId}/entitlement", "org-owner")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plan": "team",
                                  "status": "active",
                                  "seatLimit": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamEnabled").value(true));

        String invitationResponse = mockMvc.perform(post("/sessions/session-123/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectionId": "%s",
                                  "email": "pair@example.test",
                                  "displayName": "pair engineer",
                                  "role": "viewer"
                                }
                                """.formatted(attached.connectionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationToken").isNotEmpty())
                .andExpect(jsonPath("$.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.accepted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayInvitationResponse invitation = objectMapper.readValue(invitationResponse, RelayInvitationResponse.class);
        String invitedLoginResponse = mockMvc.perform(post("/sessions/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationToken": "%s",
                                  "accountId": "acct-pair-engineer"
                                }
                                """.formatted(invitation.invitationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountSessionToken").isNotEmpty())
                .andExpect(jsonPath("$.account.accountId").value("acct-pair-engineer"))
                .andExpect(jsonPath("$.account.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.account.role").value("viewer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayAccountLoginResponse invitedLogin = objectMapper.readValue(invitedLoginResponse, RelayAccountLoginResponse.class);
        mockMvc.perform(post("/sessions/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationToken": "%s",
                                  "accountId": "acct-pair-engineer-duplicate"
                                }
                                """.formatted(invitation.invitationToken())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/sessions/dashboard")
                        .queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.accountId").value("acct-pair-engineer"))
                .andExpect(jsonPath("$.organization.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.entitlement.plan").value("team"))
                .andExpect(jsonPath("$.entitlement.teamEnabled").value(true))
                .andExpect(jsonPath("$.sessions[0].sessionId").value("session-123"))
                .andExpect(jsonPath("$.sessions[0].projectId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sessions[0].projectName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sessions[0].viewerUrl").value("http://localhost:8091/view/session-123"))
                .andExpect(jsonPath("$.projects").isArray())
                .andExpect(jsonPath("$.organizationAccounts[?(@.accountId=='acct-owner')]").exists())
                .andExpect(jsonPath("$.organizationAccounts[?(@.accountId=='acct-pair-engineer')]").exists());

        mockMvc.perform(post("/sessions/dashboard/projects")
                        .queryParam("accountSession", invitedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectName": "Blocked Project"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Relay account session requires owner role"));

        String projectResponse = mockMvc.perform(post("/sessions/dashboard/projects")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectName": "Demo Service"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").isNotEmpty())
                .andExpect(jsonPath("$.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.projectName").value("Demo Service"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayProjectDescriptor project = objectMapper.readValue(projectResponse, RelayProjectDescriptor.class);
        mockMvc.perform(post("/sessions/dashboard/sessions/{sessionId}/project", "session-123")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "%s"
                                }
                                """.formatted(project.projectId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/sessions/dashboard")
                        .queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].projectId").value(project.projectId()))
                .andExpect(jsonPath("$.sessions[0].projectName").value("Demo Service"))
                .andExpect(jsonPath("$.projects[0].projectId").value(project.projectId()))
                .andExpect(jsonPath("$.projects[0].projectName").value("Demo Service"));

        mockMvc.perform(get("/sessions/dashboard/analytics")
                        .queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.sessionCount").value(1))
                .andExpect(jsonPath("$.publishedSessionCount").value(1))
                .andExpect(jsonPath("$.attachedSessionCount").value(1))
                .andExpect(jsonPath("$.replayEntryCount").value(2))
                .andExpect(jsonPath("$.debugNoteCount").value(1))
                .andExpect(jsonPath("$.recordingCount").value(1))
                .andExpect(jsonPath("$.topActors[0]").value(containsString("owner")))
                .andExpect(jsonPath("$.recentReplayTitles[0]").value("POST /users"));

        mockMvc.perform(post("/sessions/dashboard/accounts")
                        .queryParam("accountSession", invitedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Blocked Viewer",
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Relay account session requires owner role"));

        String dashboardInvitationResponse = mockMvc.perform(post("/sessions/dashboard/sessions/{sessionId}/invitations", "session-123")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dashboard-share@example.test",
                                  "displayName": "Dashboard Share",
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationToken").isNotEmpty())
                .andExpect(jsonPath("$.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.email").value("dashboard-share@example.test"))
                .andExpect(jsonPath("$.role").value("viewer"))
                .andExpect(jsonPath("$.accepted").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayInvitationResponse dashboardInvitation = objectMapper.readValue(dashboardInvitationResponse, RelayInvitationResponse.class);
        mockMvc.perform(post("/sessions/dashboard/sessions/{sessionId}/invitations", "session-123")
                        .queryParam("accountSession", invitedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "blocked-dashboard-share@example.test",
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Relay account session requires owner role"));

        mockMvc.perform(post("/sessions/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationToken": "%s",
                                  "accountId": "acct-dashboard-share"
                                }
                                """.formatted(dashboardInvitation.invitationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.accountId").value("acct-dashboard-share"))
                .andExpect(jsonPath("$.account.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.account.role").value("viewer"));

        mockMvc.perform(post("/sessions/dashboard/accounts")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-dashboard-member",
                                  "displayName": "Dashboard Member",
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-dashboard-member"))
                .andExpect(jsonPath("$.organizationId").value("org-owner"));

        mockMvc.perform(patch("/sessions/dashboard/accounts/{accountId}/role", "acct-dashboard-member")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "owner"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-dashboard-member"))
                .andExpect(jsonPath("$.role").value("owner"));

        mockMvc.perform(post("/sessions/dashboard/entitlement")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plan": "team",
                                  "status": "active",
                                  "seatLimit": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.seatLimit").value(12))
                .andExpect(jsonPath("$.teamEnabled").value(true));

        mockMvc.perform(delete("/sessions/dashboard/accounts/{accountId}", "acct-owner")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot remove an account that owns an active relay session"));

        mockMvc.perform(patch("/sessions/dashboard/accounts/{accountId}/role", "acct-owner")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot demote an account that owns an active relay session"));

        mockMvc.perform(patch("/sessions/dashboard/accounts/{accountId}/role", "acct-dashboard-member")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("viewer"));

        mockMvc.perform(delete("/sessions/dashboard/accounts/{accountId}", "acct-dashboard-member")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/sessions/dashboard/accounts/{accountId}", "acct-owner")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot remove or demote the last owner in this organization"));

        String invitedViewerSessionResponse = mockMvc.perform(post("/sessions/viewer/session-123/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountSessionToken": "%s"
                                }
                                """.formatted(invitedLogin.accountSessionToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("viewer"))
                .andExpect(jsonPath("$.viewerSessionToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayViewerSessionResponse invitedViewerSession = objectMapper.readValue(invitedViewerSessionResponse, RelayViewerSessionResponse.class);
        mockMvc.perform(get("/sessions/viewer/session-123/hosted-view")
                        .queryParam("viewerSession", invitedViewerSession.viewerSessionToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/sessions/session-123/owner/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectionId": "%s",
                                  "targetViewerSessionId": "%s"
                                }
                                """.formatted(attached.connectionId(), viewerSession.viewerSessionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.displayName").value("qa-viewer"))
                .andExpect(jsonPath("$.owner.role").value("owner"))
                .andExpect(jsonPath("$.owner.accountId").value("acct-viewer-" + viewerSession.viewerSessionId()));

        mockMvc.perform(get("/sessions/session-123/audit")
                        .queryParam("connectionId", attached.connectionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventType").isNotEmpty())
                .andExpect(jsonPath("$.items[0].organizationId").value("org-owner"))
                .andExpect(jsonPath("$.items[0].sessionId").value("session-123"));

        mockMvc.perform(get("/sessions/admin/audit").queryParam("organizationId", "org-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].organizationId").value("org-owner"));

        mockMvc.perform(get("/sessions/directory")
                        .queryParam("accountSession", rotatedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[?(@.accountId=='acct-owner')]").exists())
                .andExpect(jsonPath("$.accounts[?(@.accountId=='acct-viewer-" + viewerSession.viewerSessionId() + "')]").exists());

        mockMvc.perform(post("/sessions/artifacts/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RelayRequestArtifactPayload(
                                "session-123",
                                new CapturedRequest(
                                        "request-9",
                                        "POST",
                                        "/users",
                                        java.util.Map.of("content-type", List.of("application/json")),
                                        "{\"email\":\"pair@example.test\"}",
                                        false,
                                        false,
                                        java.time.Instant.parse("2026-04-08T00:00:00Z"),
                                        201
                                )
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/sessions/collaboration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RelaySessionCollaborationPayload(
                                "session-123",
                                List.of(new SessionActivityEventDescriptor(
                                        "session.note_added",
                                        "owner",
                                        "investigate request-9",
                                        "2026-04-08T00:02:00Z"
                                )),
                                List.of(new SessionDebugNoteDescriptor(
                                        "note-1",
                                        "owner",
                                        "need to inspect payload",
                                        "request",
                                        "request-9",
                                        "2026-04-08T00:03:00Z"
                                )),
                                List.of(new SessionRecordingDescriptor(
                                        "recording-1",
                                        "owner",
                                        "2026-04-08T00:01:00Z",
                                        null,
                                        true,
                                        4,
                                        2,
                                        1,
                                        1,
                                        "request",
                                        "request-9",
                                        List.of("session.note_added")
                                ))
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/s/session-123").queryParam("token", "viewer-token-123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hosted Session Viewer")))
                .andExpect(content().string(containsString("session-123")))
                .andExpect(content().string(containsString("Viewer session")))
                .andExpect(content().string(containsString("Session activity")))
                .andExpect(content().string(containsString("Recording snapshots")));

        mockMvc.perform(get("/app").queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hosted Dashboard")))
                .andExpect(content().string(containsString("Relay control plane")))
                .andExpect(content().string(containsString(invitedLogin.accountSessionToken())))
                .andExpect(content().string(containsString("Projects")))
                .andExpect(content().string(containsString("Usage analytics")))
                .andExpect(content().string(containsString("Session sharing")))
                .andExpect(content().string(containsString("Cloud request replay")))
                .andExpect(content().string(containsString("Remote debugging")));

        mockMvc.perform(get("/sessions/viewer/session-123/hosted-view"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/sessions/viewer/session-123/hosted-view").queryParam("viewerSession", viewerSession.viewerSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("owner"));

        mockMvc.perform(get("/sessions/viewer/session-123/artifacts/request")
                        .queryParam("viewerSession", viewerSession.viewerSessionToken())
                        .queryParam("requestId", "request-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.responseStatus").value(201));

        mockMvc.perform(post("/sessions/dashboard/sessions/{sessionId}/requests/{requestId}/replay", "session-123", "request-9")
                        .queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.requestId").value("request-9"))
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.originalStatus").value(201))
                .andExpect(jsonPath("$.replayableBody").value(true))
                .andExpect(jsonPath("$.curlCommand").value(containsString("curl -i -X")))
                .andExpect(jsonPath("$.curlCommand").value(containsString("/users")))
                .andExpect(jsonPath("$.replayHint").value(containsString("the relay does not execute customer traffic")));

        mockMvc.perform(post("/sessions/dashboard/sessions/{sessionId}/requests/{requestId}/replay", "missing", "request-9")
                        .queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/sessions/dashboard/sessions/{sessionId}/remote-debug", "session-123")
                        .queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.organizationId").value("org-owner"))
                .andExpect(jsonPath("$.requestArtifactCount").value(1))
                .andExpect(jsonPath("$.activityCount").value(1))
                .andExpect(jsonPath("$.debugNoteCount").value(1))
                .andExpect(jsonPath("$.recordingCount").value(1))
                .andExpect(jsonPath("$.recentRequests[0].requestId").value("request-9"))
                .andExpect(jsonPath("$.recentRequests[0].method").value("POST"))
                .andExpect(jsonPath("$.recentRequests[0].path").value("/users"))
                .andExpect(jsonPath("$.recentDebugNotes[0].noteId").value("note-1"));

        mockMvc.perform(post("/sessions/attach")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-999",
                                  "ownerName": "other",
                                  "relayUrl": "http://localhost:8091",
                                  "shareUrl": "http://localhost:8080",
                                  "encryptedToken": "encrypted-viewer-token-999",
                                  "expiresAt": "2026-04-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("org-other"))
                .andExpect(jsonPath("$.ownerAccountId").value("acct-other"));

        String otherOrgLoginResponse = mockMvc.perform(post("/sessions/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-other",
                                  "organizationId": "org-other"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        RelayAccountLoginResponse otherOrgLogin = objectMapper.readValue(otherOrgLoginResponse, RelayAccountLoginResponse.class);

        mockMvc.perform(post("/sessions/directory/accounts")
                        .queryParam("accountSession", otherOrgLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-outsider",
                                  "displayName": "outsider",
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("org-other"));

        String outsiderLoginResponse = mockMvc.perform(post("/sessions/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-outsider",
                                  "organizationId": "org-other"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        RelayAccountLoginResponse outsiderLogin = objectMapper.readValue(outsiderLoginResponse, RelayAccountLoginResponse.class);
        mockMvc.perform(get("/sessions/dashboard/sessions/{sessionId}/remote-debug", "session-123")
                        .queryParam("accountSession", outsiderLogin.accountSessionToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Relay account is not a member of this session organization"));

        mockMvc.perform(get("/sessions/viewer/session-123/collaboration/activity")
                        .queryParam("viewerSession", viewerSession.viewerSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventType").value("session.note_added"));

        mockMvc.perform(get("/sessions/viewer/session-123/collaboration/notes")
                        .queryParam("viewerSession", viewerSession.viewerSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].message").value("need to inspect payload"));

        mockMvc.perform(get("/sessions/viewer/session-123/collaboration/recordings")
                        .queryParam("viewerSession", viewerSession.viewerSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recordingId").value("recording-1"));

        mockMvc.perform(delete("/sessions/session-123/viewer-sessions/{viewerSessionId}", viewerSession.viewerSessionId())
                        .queryParam("connectionId", attached.connectionId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/sessions/viewer/session-123/hosted-view").queryParam("viewerSession", viewerSession.viewerSessionId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        mockMvc.perform(get("/sessions/session-123/hosted-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].viewId").value("view-1"))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(post("/sessions/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-123",
                                  "connectionId": "%s",
                                  "snapshot": {
                                    "sessionId": "session-123",
                                    "agentStatus": "ready",
                                    "relayStatus": "connected",
                                    "attached": true,
                                    "relayUrl": "wss://relay.example.test",
                                    "shareUrl": "https://app.example.test/s/session-123",
                                    "activity": [],
                                    "replay": [],
                                    "debugNotes": [],
                                    "recordings": [],
                                    "audit": [],
                                    "accessScope": "team",
                                    "allowedRoles": ["owner", "viewer"],
                                    "activeMembers": [],
                                    "workspaceMembers": [],
                                    "ownerCount": 1,
                                    "viewerMemberCount": 0,
                                    "guestMemberCount": 0,
                                    "recentActors": [],
                                    "viewerCount": 0,
                                    "activeShareTokens": 0,
                                    "ownerName": "owner",
                                    "ownerTokenPreview": "preview",
                                    "lastIssuedShareRole": "viewer",
                                    "lastIssuedShareTokenPreview": "token",
                                    "tokenMode": "local",
                                    "relayMode": "managed-relay",
                                    "relayHandshakeId": "%s",
                                    "relayConnectionId": "%s",
                                    "relayLeaseId": "%s",
                                    "relayLeaseExpiresAt": "2030-01-01T00:00:00Z",
                                    "relayViewerUrl": "http://localhost:8091/view/session-123",
                                    "relayTunnelId": "%s",
                                    "tunnelStatus": "open",
                                    "tunnelOpenedAt": "2026-04-08T00:00:00Z",
                                    "tunnelClosedAt": null,
                                    "lastError": null,
                                    "focusedArtifactType": null,
                                    "focusedArtifactId": null,
                                    "focusedBy": null,
                                    "focusedAt": null,
                                    "recordingActive": false,
                                    "currentRecordingId": null,
                                    "recordingStartedAt": null,
                                    "recordingStoppedAt": null,
                                    "syncStatus": "pending",
                                    "lastSyncId": null,
                                    "lastSyncedAt": null,
                                    "sessionVersion": 3,
                                    "publishedSessionVersion": 3,
                                    "hostedViewStatus": "available",
                                    "lastPublishedAt": "2026-04-08T00:00:00Z",
                                    "activityRetentionLimit": 50,
                                    "replayRetentionLimit": 50,
                                    "recordingRetentionLimit": 10,
                                    "auditRetentionLimit": 50,
                                    "lastHeartbeatAt": "2026-04-08T00:00:00Z",
                                    "nextHeartbeatAt": "2026-04-08T00:01:00Z",
                                    "reconnectAt": null,
                                    "lastRotatedAt": "2026-04-08T00:00:00Z",
                                    "expiresAt": "2030-01-01T00:00:00Z"
                                  }
                                }
                                """.formatted(
                                attached.connectionId(),
                                attached.handshakeId(),
                                attached.connectionId(),
                                attached.leaseId(),
                                opened.tunnelId()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.relayStatus").value("synced"));

        mockMvc.perform(post("/sessions/tunnel/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new RelayTunnelClosePayload(
                                "session-123",
                                attached.connectionId(),
                                opened.tunnelId()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tunnelStatus").value("closed"));

        mockMvc.perform(delete("/sessions/session-123/hosted-view"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/sessions/session-123/hosted-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/sessions/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCount").value(2))
                .andExpect(jsonPath("$.organizationCount").isNumber())
                .andExpect(jsonPath("$.accountCount").isNumber())
                .andExpect(jsonPath("$.attachedSessionCount").value(2))
                .andExpect(jsonPath("$.viewerSessionCount").isNumber())
                .andExpect(jsonPath("$.persistenceFile").isNotEmpty());
    }

    @Test
    void unknownSessionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/sessions/missing/hosted-view"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown relay session missing"));
    }
}
