package com.devtools.ui.autoconfigure;

import com.devtools.ui.autoconfigure.api.PagedResponse;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

class HttpHostedRelaySessionStore implements HostedRelaySessionStore {

    private static final ParameterizedTypeReference<PagedResponse<HostedSessionViewDescriptor>> HOSTED_HISTORY_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    HttpHostedRelaySessionStore(String relayApiBaseUrl, RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(trimTrailingSlash(relayApiBaseUrl))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public HostedSessionViewDescriptor current(String sessionId) {
        return get("/sessions/" + sessionId + "/hosted-view", HostedSessionViewDescriptor.class);
    }

    @Override
    public List<HostedSessionViewDescriptor> history(String sessionId) {
        PagedResponse<HostedSessionViewDescriptor> response = get(
                "/sessions/" + sessionId + "/hosted-history",
                HOSTED_HISTORY_RESPONSE
        );
        return response == null ? List.of() : response.items();
    }

    @Override
    public void storePublished(HostedSessionViewDescriptor view) {
        post("/sessions/" + view.sessionId() + "/hosted-view/published", view);
    }

    @Override
    public void storeCurrent(HostedSessionViewDescriptor view) {
        post("/sessions/" + view.sessionId() + "/hosted-view/current", view);
    }

    @Override
    public void clear(String sessionId) {
        try {
            restClient.delete()
                    .uri("/sessions/{sessionId}/hosted-view", sessionId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return;
            }
            throw new IllegalStateException("Relay request failed for hosted-view clear: " + exception.getMessage(), exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for hosted-view clear: " + exception.getMessage(), exception);
        }
    }

    private <T> T get(String path, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for " + path + ": " + exception.getMessage(), exception);
        }
    }

    private <T> T get(String path, ParameterizedTypeReference<T> responseType) {
        try {
            return restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Relay request failed for " + path + ": " + exception.getMessage(), exception);
        }
    }

    private void post(String path, HostedSessionViewDescriptor body) {
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

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
