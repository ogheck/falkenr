package com.devtools.ui.autoconfigure;

import com.devtools.ui.autoconfigure.api.PagedResponse;
import com.devtools.ui.core.model.RelaySessionIdentityDescriptor;
import com.devtools.ui.core.model.RelayViewerSessionDescriptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class HttpRelayClient implements RelayClient {

    private static final ParameterizedTypeReference<PagedResponse<RelayViewerSessionDescriptor>> VIEWER_SESSIONS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    HttpRelayClient(String relayApiBaseUrl, RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(trimTrailingSlash(relayApiBaseUrl))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public RelayAttachResult attach(RelayAttachRequest request) {
        return post("/sessions/attach", request, RelayAttachResult.class);
    }

    @Override
    public RelayHeartbeatResult heartbeat(RelayHeartbeatRequest request) {
        return post("/sessions/heartbeat", request, RelayHeartbeatResult.class);
    }

    @Override
    public RelaySyncResult sync(RelaySyncRequest request) {
        return post("/sessions/sync", request, RelaySyncResult.class);
    }

    @Override
    public RelayTunnelOpenResult openTunnel(RelayTunnelOpenRequest request) {
        return post("/sessions/tunnel/open", request, RelayTunnelOpenResult.class);
    }

    @Override
    public RelayTunnelCloseResult closeTunnel(RelayTunnelCloseRequest request) {
        return post("/sessions/tunnel/close", request, RelayTunnelCloseResult.class);
    }

    @Override
    public void registerAccessToken(RelayAccessTokenRequest request) {
        postWithoutResponse("/sessions/access-tokens/register", request);
    }

    @Override
    public void registerRequestArtifact(RelayRequestArtifactRequest request) {
        postWithoutResponse("/sessions/artifacts/request", request);
    }

    @Override
    public void registerSessionCollaboration(RelaySessionCollaborationRequest request) {
        postWithoutResponse("/sessions/collaboration", request);
    }

    @Override
    public java.util.List<RelayViewerSessionDescriptor> viewerSessions(String sessionId, String connectionId) {
        PagedResponse<RelayViewerSessionDescriptor> response = get(
                "/sessions/" + sessionId + "/viewer-sessions?connectionId=" + connectionId,
                VIEWER_SESSIONS_RESPONSE
        );
        return response.items();
    }

    @Override
    public void revokeViewerSession(String sessionId, String connectionId, String viewerSessionId) {
        try {
            restClient.delete()
                    .uri("/sessions/{sessionId}/viewer-sessions/{viewerSessionId}?connectionId={connectionId}",
                            sessionId, viewerSessionId, connectionId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for viewer session revoke: " + exception.getMessage(), exception);
        }
    }

    @Override
    public RelaySessionIdentityDescriptor sessionIdentity(String sessionId, String connectionId) {
        return get(
                "/sessions/" + sessionId + "/identity?connectionId=" + connectionId,
                RelaySessionIdentityDescriptor.class
        );
    }

    @Override
    public RelaySessionIdentityDescriptor transferOwner(String sessionId, String connectionId, String targetViewerSessionId) {
        return post(
                "/sessions/" + sessionId + "/owner/transfer",
                new RelayOwnerTransferRequest(connectionId, targetViewerSessionId),
                RelaySessionIdentityDescriptor.class
        );
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new IllegalStateException("Relay returned an empty response for " + path);
            }
            return response;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for " + path + ": " + exception.getMessage(), exception);
        }
    }

    private void postWithoutResponse(String path, Object body) {
        try {
            restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for " + path + ": " + exception.getMessage(), exception);
        }
    }

    private <T> T get(String path, ParameterizedTypeReference<T> responseType) {
        try {
            T response = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new IllegalStateException("Relay returned an empty response for " + path);
            }
            return response;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for " + path + ": " + exception.getMessage(), exception);
        }
    }

    private <T> T get(String path, Class<T> responseType) {
        try {
            T response = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new IllegalStateException("Relay returned an empty response for " + path);
            }
            return response;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for " + path + ": " + exception.getMessage(), exception);
        }
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
