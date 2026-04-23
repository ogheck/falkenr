package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.RemoteSessionDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteSessionTunnelStreamTest {

    @Test
    void openTunnelMovesSessionToStreamingWhenRelayStreamConnects() {
        DevToolsUiProperties.RemoteSettings settings = new DevToolsUiProperties.RemoteSettings();
        LocalAgentSessionStore sessionStore = new LocalAgentSessionStore(Clock.systemUTC(), settings, new DevToolsUiProperties.SecretsSettings(), new DefaultDevToolsDataPolicy(new DevToolsUiProperties.PolicySettings()));
        RemoteSessionService service = new RemoteSessionService(
                sessionStore,
                new LocalRelayClient(settings),
                new RelayTokenCodec(Clock.systemUTC()),
                new InMemoryHostedRelaySessionStore(),
                new NoOpTunnelStreamClient()
        );

        service.attach(new RemoteSessionAttachRequest("pair-debugger", false));
        RemoteSessionDescriptor session = service.openTunnel();

        assertThat(session.relayTunnelId()).isNotBlank();
        assertThat(session.tunnelStatus()).isEqualTo("streaming");
    }

    @Test
    void tunnelStreamFailuresSurfaceInSessionState() {
        DevToolsUiProperties.RemoteSettings settings = new DevToolsUiProperties.RemoteSettings();
        LocalAgentSessionStore sessionStore = new LocalAgentSessionStore(Clock.systemUTC(), settings, new DevToolsUiProperties.SecretsSettings(), new DefaultDevToolsDataPolicy(new DevToolsUiProperties.PolicySettings()));
        TunnelStreamClient failingClient = (streamUrl, listener) -> {
            listener.onError("stream handshake failed");
            return listener::onClosed;
        };
        RemoteSessionService service = new RemoteSessionService(
                sessionStore,
                new LocalRelayClient(settings),
                new RelayTokenCodec(Clock.systemUTC()),
                new InMemoryHostedRelaySessionStore(),
                failingClient
        );

        service.attach(new RemoteSessionAttachRequest("pair-debugger", false));
        RemoteSessionDescriptor session = service.openTunnel();

        assertThat(session.tunnelStatus()).isEqualTo("stream-failed");
        assertThat(session.lastError()).contains("stream handshake failed");
        assertThat(session.reconnectAt()).isNotNull();
    }

    @Test
    void heartbeatReopensTunnelAfterReconnectDelay() throws Exception {
        DevToolsUiProperties.RemoteSettings settings = new DevToolsUiProperties.RemoteSettings();
        settings.setReconnectDelaySeconds(1);
        LocalAgentSessionStore sessionStore = new LocalAgentSessionStore(Clock.systemUTC(), settings, new DevToolsUiProperties.SecretsSettings(), new DefaultDevToolsDataPolicy(new DevToolsUiProperties.PolicySettings()));
        AtomicInteger openCount = new AtomicInteger();
        RelayClient relayClient = new RelayClient() {
            @Override
            public RelayAttachResult attach(RelayAttachRequest request) {
                return new RelayAttachResult(
                        "hs-1",
                        "conn-1",
                        "connected",
                        "lease-1",
                        Instant.now().plusSeconds(60).toString(),
                        "http://viewer",
                        "org-managed",
                        "Managed Workspace",
                        "acct-owner-1"
                );
            }

            @Override
            public RelayHeartbeatResult heartbeat(RelayHeartbeatRequest request) {
                return new RelayHeartbeatResult("connected", "healthy", true, "lease-2", Instant.now().plusSeconds(60).toString());
            }

            @Override
            public RelaySyncResult sync(RelaySyncRequest request) {
                return new RelaySyncResult("sync-1", "view-1", "connected", true);
            }

            @Override
            public RelayTunnelOpenResult openTunnel(RelayTunnelOpenRequest request) {
                int count = openCount.incrementAndGet();
                return new RelayTunnelOpenResult("tunnel-" + count, "open", Instant.now().toString(), "http://stream/" + count);
            }

            @Override
            public RelayTunnelCloseResult closeTunnel(RelayTunnelCloseRequest request) {
                return new RelayTunnelCloseResult("closed", Instant.now().toString());
            }

            @Override
            public void registerAccessToken(RelayAccessTokenRequest request) {
            }

            @Override
            public void registerRequestArtifact(RelayRequestArtifactRequest request) {
            }

            @Override
            public void registerSessionCollaboration(RelaySessionCollaborationRequest request) {
            }

            @Override
            public java.util.List<com.devtools.ui.core.model.RelayViewerSessionDescriptor> viewerSessions(String sessionId, String connectionId) {
                return java.util.List.of();
            }

            @Override
            public void revokeViewerSession(String sessionId, String connectionId, String viewerSessionId) {
            }

            @Override
            public com.devtools.ui.core.model.RelaySessionIdentityDescriptor sessionIdentity(String sessionId, String connectionId) {
                return new com.devtools.ui.core.model.RelaySessionIdentityDescriptor(
                        sessionId,
                        new com.devtools.ui.core.model.RelayOrganizationDescriptor("org-managed", "Managed Workspace"),
                        new com.devtools.ui.core.model.RelayAccountDescriptor("acct-owner-1", "pair-debugger", "org-managed", "owner"),
                        java.util.List.of()
                );
            }

            @Override
            public com.devtools.ui.core.model.RelaySessionIdentityDescriptor transferOwner(String sessionId, String connectionId, String targetViewerSessionId) {
                throw new UnsupportedOperationException();
            }
        };
        TunnelStreamClient flakyClient = new TunnelStreamClient() {
            @Override
            public TunnelStreamHandle connect(String streamUrl, TunnelEventListener listener) {
                if (streamUrl.endsWith("/1")) {
                    listener.onError("stream dropped");
                } else {
                    listener.onConnected();
                }
                return () -> {
                };
            }
        };
        RemoteSessionService service = new RemoteSessionService(
                sessionStore,
                relayClient,
                new RelayTokenCodec(Clock.systemUTC()),
                new InMemoryHostedRelaySessionStore(),
                flakyClient
        );

        service.attach(new RemoteSessionAttachRequest("pair-debugger", false));
        RemoteSessionDescriptor failed = service.openTunnel();
        assertThat(failed.tunnelStatus()).isEqualTo("stream-failed");

        Thread.sleep(1100);

        RemoteSessionDescriptor recovered = service.heartbeat();
        assertThat(recovered.relayTunnelId()).isEqualTo("tunnel-2");
        assertThat(recovered.tunnelStatus()).isEqualTo("streaming");
        assertThat(openCount.get()).isEqualTo(2);
    }
}
