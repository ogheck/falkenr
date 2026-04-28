package com.devtools.ui.relay;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRelaySessionStorePersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void reloadsPersistedSessionStateFromDisk() {
        Path persistenceFile = tempDir.resolve("relay-state.json");
        RelayServerProperties properties = new RelayServerProperties(
                "http://localhost:8091",
                Duration.ofMinutes(10),
                50,
                persistenceFile.toString(),
                "test-relay-issuer",
                "test-relay-secret",
                "",
                "",
                "",
                "",
                ""
        );
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        InMemoryRelaySessionStore firstStore = new InMemoryRelaySessionStore(properties, objectMapper);
        RelayAttachResponse attached = firstStore.attach(new RelayAttachPayload(
                "session-123",
                "owner",
                List.of("owner", "viewer"),
                "wss://relay.example.test",
                "https://app.example.test/s/session-123",
                "encrypted-token",
                "2030-01-01T00:00:00Z"
        ));
        firstStore.storeCurrentView("session-123", new HostedSessionViewDescriptor(
                "session-123",
                "view-1",
                "sync-1",
                true,
                "relay-backed",
                3L,
                3L,
                "http://localhost:8091/view/session-123",
                "owner",
                "team",
                1,
                1,
                1,
                1,
                "request",
                "request-9",
                "2026-04-08T00:00:00Z",
                List.of(),
                List.of("owner"),
                List.of("POST /users")
        ));
        firstStore.storeRequestArtifact(new RelayRequestArtifactPayload(
                "session-123",
                new CapturedRequest(
                        "request-9",
                        "POST",
                        "/users",
                        java.util.Map.of("content-type", List.of("application/json")),
                        "{\"email\":\"pair@example.test\"}",
                        false,
                        false,
                        Instant.parse("2026-04-08T00:00:00Z"),
                        201
                )
        ));
        firstStore.storeCollaboration(new RelaySessionCollaborationPayload(
                "session-123",
                List.of(new SessionActivityEventDescriptor(
                        "session.inspect",
                        "owner",
                        "request:request-9",
                        "2026-04-08T00:01:00Z"
                )),
                List.of(new SessionDebugNoteDescriptor(
                        "note-1",
                        "owner",
                        "check payload",
                        "request",
                        "request-9",
                        "2026-04-08T00:02:00Z"
                )),
                List.of(new SessionRecordingDescriptor(
                        "recording-1",
                        "owner",
                        "2026-04-08T00:03:00Z",
                        null,
                        true,
                        1,
                        1,
                        1,
                        1,
                        "request",
                        "request-9",
                        List.of("session.inspect")
                ))
        ));
        firstStore.upsertOrganization(new RelayOrganizationRequest("org-alpha-team", "Alpha Team"));
        firstStore.upsertAccount(new RelayAccountRequest("acct-alpha-member", "Alpha Member", "org-alpha-team", "viewer"));

        InMemoryRelaySessionStore reloadedStore = new InMemoryRelaySessionStore(properties, objectMapper);

        assertThat(reloadedStore.currentHostedView("session-123")).isNotNull();
        assertThat(reloadedStore.currentHostedView("session-123").ownerName()).isEqualTo("owner");
        assertThat(reloadedStore.organizations()).extracting(com.devtools.ui.core.model.RelayOrganizationDescriptor::organizationId)
                .contains("org-owner");
        assertThat(reloadedStore.accounts("org-owner")).extracting(com.devtools.ui.core.model.RelayAccountDescriptor::accountId)
                .contains("acct-owner");
        assertThat(reloadedStore.organizations()).extracting(com.devtools.ui.core.model.RelayOrganizationDescriptor::organizationId)
                .contains("org-alpha-team");
        assertThat(reloadedStore.accounts("org-alpha-team")).extracting(com.devtools.ui.core.model.RelayAccountDescriptor::accountId)
                .containsExactly("acct-alpha-member");
        assertThat(reloadedStore.requestArtifact("session-123", "request-9").path()).isEqualTo("/users");
        assertThat(reloadedStore.collaborationActivity("session-123")).extracting(SessionActivityEventDescriptor::eventType)
                .containsExactly("session.inspect");
        assertThat(reloadedStore.collaborationDebugNotes("session-123")).extracting(SessionDebugNoteDescriptor::message)
                .containsExactly("check payload");
        assertThat(reloadedStore.collaborationRecordings("session-123")).extracting(SessionRecordingDescriptor::recordingId)
                .containsExactly("recording-1");
        assertThat(reloadedStore.heartbeat(new RelayHeartbeatPayload("session-123", attached.connectionId())).connected()).isTrue();
    }

    @Test
    void exportsAndImportsRelayStateSnapshot() {
        Path persistenceFile = tempDir.resolve("relay-admin-state.json");
        RelayServerProperties properties = new RelayServerProperties(
                "http://localhost:8091",
                Duration.ofMinutes(10),
                50,
                persistenceFile.toString(),
                "test-relay-issuer",
                "test-relay-secret",
                "",
                "",
                "",
                "",
                ""
        );
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        InMemoryRelaySessionStore firstStore = new InMemoryRelaySessionStore(properties, objectMapper);
        firstStore.attach(new RelayAttachPayload(
                "session-export",
                "owner",
                List.of("owner", "viewer"),
                "wss://relay.example.test",
                "https://app.example.test/s/session-export",
                "encrypted-token",
                "2030-01-01T00:00:00Z"
        ));
        firstStore.upsertOrganization(new RelayOrganizationRequest("org-export", "Export Org"));
        firstStore.upsertAccount(new RelayAccountRequest("acct-export", "Export User", "org-export", "viewer"));
        firstStore.upsertEntitlement("org-export", new RelayEntitlementRequest("team", "active", 3));

        Object exported = firstStore.exportState();

        InMemoryRelaySessionStore secondStore = new InMemoryRelaySessionStore(
                new RelayServerProperties(
                        "http://localhost:8091",
                        Duration.ofMinutes(10),
                        50,
                        tempDir.resolve("relay-admin-imported.json").toString(),
                        "test-relay-issuer",
                        "test-relay-secret",
                        "",
                        "",
                        "",
                        "",
                        ""
                ),
                objectMapper
        );
        RelayAdminImportResponse response = secondStore.importState(exported);

        assertThat(response.status()).isEqualTo("imported");
        assertThat(secondStore.organizations()).extracting(com.devtools.ui.core.model.RelayOrganizationDescriptor::organizationId)
                .contains("org-export");
        assertThat(secondStore.accounts("org-export")).extracting(com.devtools.ui.core.model.RelayAccountDescriptor::accountId)
                .contains("acct-export");
        assertThat(secondStore.entitlement("org-export").teamEnabled()).isTrue();
    }

    @Test
    void reloadsLegacyViewerSessionsWithoutAccountIds() throws IOException {
        Path persistenceFile = tempDir.resolve("relay-legacy-state.json");
        Files.writeString(
                persistenceFile,
                """
                        {
                          "sessions": [
                            {
                              "sessionId": "session-legacy",
                              "history": [],
                              "allowedRoles": ["owner", "viewer"],
                              "ownerName": "owner",
                              "relayUrl": "wss://relay.example.test",
                              "shareUrl": "https://app.example.test/s/session-legacy",
                              "organizationId": "org-owner",
                              "organizationName": "owner Workspace",
                              "ownerAccountId": "acct-owner",
                              "encryptedToken": "encrypted-token",
                              "expiresAt": "2030-01-01T00:00:00Z",
                              "handshakeId": "hs-legacy",
                              "connectionId": "conn-legacy",
                              "leaseId": "lease-legacy",
                              "leaseExpiresAt": "2030-01-01T00:00:00Z",
                              "relayStatus": "connected",
                              "tunnelStatus": "open",
                              "tunnelId": "tunnel-legacy",
                              "tunnelOpenedAt": "2026-04-08T00:00:00Z",
                              "tunnelClosedAt": null,
                              "lastAttachedAt": "2026-04-08T00:00:00Z",
                              "lastSyncId": null,
                              "lastPublishedAt": null,
                              "sessionVersion": 1,
                              "currentView": null,
                              "accessTokens": {},
                              "viewerSessions": {
                                "viewer_legacy": {
                                  "viewerSessionId": "viewer_legacy",
                                  "accountId": null,
                                  "role": "viewer",
                                  "expiresAt": "2030-01-01T00:00:00Z",
                                  "viewerName": "Legacy Viewer",
                                  "createdAt": "2026-04-08T00:01:00Z"
                                }
                              },
                              "requestArtifacts": {},
                              "activity": [],
                              "debugNotes": [],
                              "recordings": []
                            }
                          ],
                          "organizations": {},
                          "accounts": {},
                          "accountSessions": {},
                          "invitations": {},
                          "entitlements": {},
                          "auditEvents": []
                        }
                        """
        );
        RelayServerProperties properties = new RelayServerProperties(
                "http://localhost:8091",
                Duration.ofMinutes(10),
                50,
                persistenceFile.toString(),
                "test-relay-issuer",
                "test-relay-secret",
                "",
                "",
                "",
                "",
                ""
        );
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

        InMemoryRelaySessionStore reloadedStore = new InMemoryRelaySessionStore(properties, objectMapper);

        assertThat(reloadedStore.accounts("org-owner")).extracting(com.devtools.ui.core.model.RelayAccountDescriptor::accountId)
                .contains("acct-viewer-viewer_legacy");
    }
}
