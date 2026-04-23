package com.devtools.ui.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.devtools.ui.relay-server.persistence-file=${java.io.tmpdir}/spring-devtools-ui-relay-server-smoke-state-${random.uuid}.json",
        "spring.devtools.ui.relay-server.auth-secret=test-relay-secret",
        "spring.devtools.ui.relay-server.auth-issuer=test-relay-issuer"
})
@AutoConfigureMockMvc
class RelayHostedDashboardSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void homepageAndBuiltAssetsAreServed() throws Exception {
        String homepage = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<title>Falkenr</title>")))
                .andExpect(content().string(containsString("./assets/")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jsHref = homepage.replaceAll("(?s).*src=\"\\./assets/([^\"]+\\.js)\".*", "$1");
        String cssHref = homepage.replaceAll("(?s).*href=\"\\./assets/([^\"]+\\.css)\".*", "$1");

        mockMvc.perform(get("/assets/{asset}", jsHref))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));

        mockMvc.perform(get("/assets/{asset}", cssHref))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));

        mockMvc.perform(get("/logo.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"));
    }

    @Test
    void hostedDashboardLoginInviteAndOpenSessionWork() throws Exception {
        // Bootstrap an org/owner account via attach.
        String attachResponse = mockMvc.perform(post("/sessions/attach")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "session-123",
                                  "ownerName": "owner",
                                  "relayUrl": "http://localhost:8091",
                                  "shareUrl": "http://localhost:8080",
                                  "encryptedToken": "encrypted-viewer-token-123",
                                  "expiresAt": "2026-04-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayAttachResponse attached = objectMapper.readValue(attachResponse, RelayAttachResponse.class);

        // Make sure hosted dashboard UI is served.
        mockMvc.perform(get("/app"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hosted Dashboard")))
                .andExpect(content().string(containsString("Sign in")));

        // Owner login.
        String ownerLoginResponse = mockMvc.perform(post("/sessions/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-owner",
                                  "organizationId": "org-owner"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountSessionToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayAccountLoginResponse ownerLogin = objectMapper.readValue(ownerLoginResponse, RelayAccountLoginResponse.class);

        // Dashboard API works for logged in owner.
        mockMvc.perform(get("/sessions/dashboard")
                        .queryParam("accountSession", ownerLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.organizationId").value("org-owner"));

        // Enable Team so invitations are allowed.
        mockMvc.perform(post("/sessions/dashboard/entitlement")
                        .queryParam("accountSession", ownerLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plan": "team",
                                  "status": "active",
                                  "seatLimit": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamEnabled").value(true));

        // Issue an invitation.
        String invitationResponse = mockMvc.perform(post("/sessions/dashboard/sessions/{sessionId}/invitations", "session-123")
                        .queryParam("accountSession", ownerLogin.accountSessionToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "viewer@example.test",
                                  "displayName": "Viewer",
                                  "role": "viewer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayInvitationResponse invitation = objectMapper.readValue(invitationResponse, RelayInvitationResponse.class);

        // Accept invitation to mint an account session.
        String invitedLoginResponse = mockMvc.perform(post("/sessions/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationToken": "%s",
                                  "accountId": "acct-invited"
                                }
                                """.formatted(invitation.invitationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountSessionToken").isNotEmpty())
                .andExpect(jsonPath("$.account.organizationId").value("org-owner"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        RelayAccountLoginResponse invitedLogin = objectMapper.readValue(invitedLoginResponse, RelayAccountLoginResponse.class);

        // `/app` renders a permalink-able dashboard with accountSession in the HTML.
        mockMvc.perform(get("/app").queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(invitedLogin.accountSessionToken())));

        // Open a hosted session viewer page using accountSession (the server will mint a viewerSession JWT and inject it).
        mockMvc.perform(get("/s/{sessionId}", "session-123")
                        .queryParam("accountSession", invitedLogin.accountSessionToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("const sessionId = 'session-123'")))
                .andExpect(content().string(containsString("const viewerSession = '")));

        // Sanity: session is still attached.
        mockMvc.perform(get("/sessions/session-123/identity")
                        .queryParam("connectionId", attached.connectionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"));
    }
}
