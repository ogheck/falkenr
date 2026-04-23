package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.HostedSessionMemberDescriptor;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpHostedRelaySessionStoreTest {

    @Test
    void readsAndWritesHostedSessionStateThroughManagedRelayApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHostedRelaySessionStore store = new HttpHostedRelaySessionStore("https://relay.example.test/api", builder);
        HostedSessionViewDescriptor view = hostedView();

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/hosted-view/current"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withNoContent());

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/hosted-view/published"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withNoContent());

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/hosted-view"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "sessionId": "session-123",
                          "viewId": "view-123",
                          "syncId": "sync-123",
                          "available": true,
                          "durableState": "published",
                          "sessionVersion": 7,
                          "publishedVersion": 7,
                          "relayViewerUrl": "https://relay.example.test/view/session-123",
                          "ownerName": "pair-debugger",
                          "accessScope": "owner-viewer-guest",
                          "activeMemberCount": 2,
                          "replayCount": 4,
                          "debugNoteCount": 1,
                          "recordingCount": 1,
                          "focusedArtifactType": "request",
                          "focusedArtifactId": "req-1",
                          "lastPublishedAt": "2026-04-08T19:00:00Z",
                          "members": [
                            {
                              "memberId": "owner",
                              "role": "owner",
                              "source": "local-session",
                              "joinedAt": "2026-04-08T18:00:00Z",
                              "publishedAt": "2026-04-08T19:00:00Z",
                              "focusedArtifactType": "request",
                              "focusedArtifactId": "req-1",
                              "lastAction": "session.attached"
                            }
                          ],
                          "recentActors": ["pair-debugger"],
                          "recentReplayTitles": ["POST /echo"]
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/hosted-history"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "sessionId": "session-123",
                              "viewId": "view-123",
                              "syncId": "sync-123",
                              "available": true,
                              "durableState": "published",
                              "sessionVersion": 7,
                              "publishedVersion": 7,
                              "relayViewerUrl": "https://relay.example.test/view/session-123",
                              "ownerName": "pair-debugger",
                              "accessScope": "owner-viewer-guest",
                              "activeMemberCount": 2,
                              "replayCount": 4,
                              "debugNoteCount": 1,
                              "recordingCount": 1,
                              "focusedArtifactType": "request",
                              "focusedArtifactId": "req-1",
                              "lastPublishedAt": "2026-04-08T19:00:00Z",
                              "members": [],
                              "recentActors": ["pair-debugger"],
                              "recentReplayTitles": ["POST /echo"]
                            }
                          ],
                          "total": 1,
                          "offset": 0,
                          "limit": 10
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/hosted-view"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        store.storeCurrent(view);
        store.storePublished(view);
        assertThat(store.current("session-123").viewId()).isEqualTo("view-123");
        assertThat(store.history("session-123")).extracting(HostedSessionViewDescriptor::syncId).containsExactly("sync-123");
        store.clear("session-123");
        server.verify();
    }

    @Test
    void ignoresMissingHostedViewWhenClearingBeforeFirstAttach() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpHostedRelaySessionStore store = new HttpHostedRelaySessionStore("https://relay.example.test/api", builder);

        server.expect(requestTo("https://relay.example.test/api/sessions/session-123/hosted-view"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> store.clear("session-123")).doesNotThrowAnyException();
        server.verify();
    }

    private HostedSessionViewDescriptor hostedView() {
        return new HostedSessionViewDescriptor(
                "session-123",
                "view-123",
                "sync-123",
                true,
                "published",
                7L,
                7L,
                "https://relay.example.test/view/session-123",
                "pair-debugger",
                "owner-viewer-guest",
                2,
                4,
                1,
                1,
                "request",
                "req-1",
                "2026-04-08T19:00:00Z",
                List.of(new HostedSessionMemberDescriptor(
                        "owner",
                        "owner",
                        "local-session",
                        "2026-04-08T18:00:00Z",
                        "2026-04-08T19:00:00Z",
                        "request",
                        "req-1",
                        "session.attached"
                )),
                List.of("pair-debugger"),
                List.of("POST /echo")
        );
    }
}
