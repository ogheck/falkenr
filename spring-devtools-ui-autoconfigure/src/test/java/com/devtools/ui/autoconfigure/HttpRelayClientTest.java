package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.model.RelayViewerSessionDescriptor;
import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRelayClientTest {

    @Test
    void attachPostsToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/attach"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "handshakeId": "hs-managed",
                          "connectionId": "conn-managed",
                          "relayStatus": "connected",
                          "leaseId": "lease-managed",
                          "leaseExpiresAt": "2026-04-09T00:05:00Z",
                          "viewerUrl": "https://relay.example.test/view/sessions/session-123",
                          "organizationId": "org-pair-debugger",
                          "organizationName": "pair-debugger Workspace",
                          "ownerAccountId": "acct-pair-debugger"
                        }
                        """, MediaType.APPLICATION_JSON));

        RelayAttachResult result = client.attach(new RelayAttachRequest(
                "session-123",
                "pair-debugger",
                List.of("owner", "viewer"),
                "wss://relay.example.test/sessions/session-123",
                "https://app.example.test/s/session-123",
                "encrypted-token",
                "2026-04-09T00:00:00Z"
        ));

        assertThat(result.handshakeId()).isEqualTo("hs-managed");
        assertThat(result.connectionId()).isEqualTo("conn-managed");
        assertThat(result.relayStatus()).isEqualTo("connected");
        assertThat(result.leaseId()).isEqualTo("lease-managed");
        assertThat(result.leaseExpiresAt()).isEqualTo("2026-04-09T00:05:00Z");
        assertThat(result.viewerUrl()).isEqualTo("https://relay.example.test/view/sessions/session-123");
        assertThat(result.organizationId()).isEqualTo("org-pair-debugger");
        assertThat(result.organizationName()).isEqualTo("pair-debugger Workspace");
        assertThat(result.ownerAccountId()).isEqualTo("acct-pair-debugger");
        server.verify();
    }

    @Test
    void heartbeatPostsToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/heartbeat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "relayStatus": "connected",
                          "tunnelStatus": "healthy",
                          "connected": true,
                          "leaseId": "lease-managed",
                          "leaseExpiresAt": "2026-04-09T00:10:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        RelayHeartbeatResult result = client.heartbeat(new RelayHeartbeatRequest("session-123", "conn-managed"));

        assertThat(result.relayStatus()).isEqualTo("connected");
        assertThat(result.tunnelStatus()).isEqualTo("healthy");
        assertThat(result.connected()).isTrue();
        assertThat(result.leaseId()).isEqualTo("lease-managed");
        assertThat(result.leaseExpiresAt()).isEqualTo("2026-04-09T00:10:00Z");
        server.verify();
    }

    @Test
    void openTunnelPostsToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/tunnel/open"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "tunnelId": "tunnel-managed",
                          "tunnelStatus": "open",
                          "openedAt": "2026-04-09T00:06:00Z",
                          "streamUrl": "https://relay.example.test/api/sessions/tunnel/tunnel-managed/stream?connectionId=conn-managed"
                        }
                        """, MediaType.APPLICATION_JSON));

        RelayTunnelOpenResult result = client.openTunnel(new RelayTunnelOpenRequest("session-123", "conn-managed"));

        assertThat(result.tunnelId()).isEqualTo("tunnel-managed");
        assertThat(result.tunnelStatus()).isEqualTo("open");
        assertThat(result.openedAt()).isEqualTo("2026-04-09T00:06:00Z");
        assertThat(result.streamUrl()).isEqualTo("https://relay.example.test/api/sessions/tunnel/tunnel-managed/stream?connectionId=conn-managed");
        server.verify();
    }

    @Test
    void closeTunnelPostsToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/tunnel/close"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "tunnelStatus": "closed",
                          "closedAt": "2026-04-09T00:08:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        RelayTunnelCloseResult result = client.closeTunnel(new RelayTunnelCloseRequest(
                "session-123",
                "conn-managed",
                "tunnel-managed"
        ));

        assertThat(result.tunnelStatus()).isEqualTo("closed");
        assertThat(result.closedAt()).isEqualTo("2026-04-09T00:08:00Z");
        server.verify();
    }

    @Test
    void syncPostsSessionSnapshotToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/sync"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "syncId": "sync-managed",
                          "viewId": "view-managed",
                          "relayStatus": "connected",
                          "accepted": true
                        }
                        """, MediaType.APPLICATION_JSON));

        RelaySyncResult result = client.sync(new RelaySyncRequest(
                "session-123",
                "conn-managed",
                new com.devtools.ui.core.model.RemoteSessionDescriptor(
                        "session-123",
                        "ready",
                        "connected",
                        true,
                        "wss://relay.example.test/sessions/session-123",
                        "https://app.example.test/s/session-123",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "owner-viewer",
                        List.of("owner", "viewer"),
                        List.of(),
                        List.of(),
                        1,
                        0,
                        0,
                        List.of("pair-debugger"),
                        0,
                        0,
                        "pair-debugger",
                        "org-pair-debugger",
                        "pair-debugger Workspace",
                        "acct-pair-debugger",
                        "token-preview",
                        null,
                        "[unavailable]",
                        "aes-gcm",
                        "managed-relay",
                        "hs-managed",
                        "conn-managed",
                        "lease-managed",
                        "2026-04-09T00:10:00Z",
                        "https://relay.example.test/view/sessions/session-123",
                        "tunnel-managed",
                        "healthy",
                        "2026-04-09T00:06:00Z",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        "idle",
                        null,
                        null,
                        4L,
                        null,
                        "unpublished",
                        null,
                        25,
                        50,
                        10,
                        50,
                        "2026-04-08T16:00:00Z",
                        "2026-04-08T16:30:00Z",
                        null,
                        "2026-04-08T20:00:00Z",
                        "2026-04-09T00:00:00Z"
                )
        ));

        assertThat(result.syncId()).isEqualTo("sync-managed");
        assertThat(result.viewId()).isEqualTo("view-managed");
        assertThat(result.relayStatus()).isEqualTo("connected");
        assertThat(result.accepted()).isTrue();
        server.verify();
    }

    @Test
    void registerAccessTokenPostsToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/access-tokens/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        client.registerAccessToken(new RelayAccessTokenRequest(
                "session-123",
                "share_token_123",
                "viewer",
                "2026-04-09T00:15:00Z"
        ));

        server.verify();
    }

    @Test
    void registerRequestArtifactPostsToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/artifacts/request"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        client.registerRequestArtifact(new RelayRequestArtifactRequest(
                "session-123",
                new CapturedRequest(
                        "request-9",
                        "POST",
                        "/users",
                        Map.of("content-type", List.of("application/json")),
                        "{\"email\":\"pair@example.test\"}",
                        false,
                        false,
                        java.time.Instant.parse("2026-04-08T00:00:00Z"),
                        201
                )
        ));

        server.verify();
    }

    @Test
    void registerSessionCollaborationPostsToManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/collaboration"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        client.registerSessionCollaboration(new RelaySessionCollaborationRequest(
                "session-123",
                List.of(new SessionActivityEventDescriptor(
                        "session.inspect",
                        "owner",
                        "request:request-9",
                        "2026-04-08T00:00:00Z"
                )),
                List.of(new SessionDebugNoteDescriptor(
                        "note-1",
                        "owner",
                        "follow request trail",
                        "request",
                        "request-9",
                        "2026-04-08T00:01:00Z"
                )),
                List.of(new SessionRecordingDescriptor(
                        "recording-1",
                        "owner",
                        "2026-04-08T00:02:00Z",
                        null,
                        true,
                        2,
                        1,
                        1,
                        1,
                        "request",
                        "request-9",
                        List.of("session.inspect")
                ))
        ));

        server.verify();
    }

    @Test
    void viewerSessionsGetAndDeleteUseManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/viewer-sessions?connectionId=conn-managed"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "viewerSessionId": "viewer-1",
                              "role": "viewer",
                              "viewerName": "qa-viewer",
                              "createdAt": "2026-04-09T00:00:00Z",
                              "expiresAt": "2026-04-09T01:00:00Z"
                            }
                          ],
                          "total": 1,
                          "offset": 0,
                          "limit": 1
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/viewer-sessions/viewer-1?connectionId=conn-managed"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        assertThat(client.viewerSessions("session-123", "conn-managed"))
                .extracting(RelayViewerSessionDescriptor::viewerSessionId)
                .containsExactly("viewer-1");
        client.revokeViewerSession("session-123", "conn-managed", "viewer-1");

        server.verify();
    }

    @Test
    void identityAndOwnerTransferUseManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        HttpRelayClient client = new HttpRelayClient("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/identity?connectionId=conn-managed"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "sessionId": "session-123",
                          "organization": {
                            "organizationId": "org-pair-debugger",
                            "organizationName": "pair-debugger Workspace"
                          },
                          "owner": {
                            "accountId": "acct-pair-debugger",
                            "displayName": "pair-debugger",
                            "organizationId": "org-pair-debugger",
                            "role": "owner"
                          },
                          "collaborators": [
                            {
                              "accountId": "acct-viewer-viewer-1",
                              "displayName": "qa-viewer",
                              "organizationId": "org-pair-debugger",
                              "role": "viewer"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/owner/transfer"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "connectionId": "conn-managed",
                          "targetViewerSessionId": "viewer-1"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "sessionId": "session-123",
                          "organization": {
                            "organizationId": "org-pair-debugger",
                            "organizationName": "pair-debugger Workspace"
                          },
                          "owner": {
                            "accountId": "acct-viewer-viewer-1",
                            "displayName": "qa-viewer",
                            "organizationId": "org-pair-debugger",
                            "role": "owner"
                          },
                          "collaborators": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.sessionIdentity("session-123", "conn-managed").owner().displayName()).isEqualTo("pair-debugger");
        assertThat(client.transferOwner("session-123", "conn-managed", "viewer-1").owner().displayName()).isEqualTo("qa-viewer");

        server.verify();
    }
}
