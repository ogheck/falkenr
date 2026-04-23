package com.devtools.ui.autoconfigure;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.Map;

class WebhookSimulator {

    private final ApplicationContext applicationContext;
    private final RestClient restClient;

    WebhookSimulator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.restClient = RestClient.builder().build();
    }

    WebhookDeliveryResult deliver(WebhookDeliveryRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (request.headers() != null) {
            request.headers().forEach(headers::set);
        }

        HttpEntity<String> entity = new HttpEntity<>(request.body() == null ? "{}" : request.body(), headers);
        ResponseEntity<String> response = restClient.post()
                .uri(localUrl(request.path()))
                .headers(httpHeaders -> copyHeaders(headers, httpHeaders))
                .body(entity.getBody())
                .retrieve()
                .toEntity(String.class);

        return new WebhookDeliveryResult(
                "POST",
                request.path(),
                response.getStatusCode().value(),
                response.getBody() == null ? "" : response.getBody()
        );
    }

    private String localUrl(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (!(applicationContext instanceof WebServerApplicationContext webServerApplicationContext)) {
            throw new IllegalStateException("Webhook simulator requires a running web server application context");
        }
        return "http://127.0.0.1:" + webServerApplicationContext.getWebServer().getPort() + normalizedPath;
    }

    private void copyHeaders(HttpHeaders source, HttpHeaders target) {
        for (Map.Entry<String, java.util.List<String>> entry : source.entrySet()) {
            target.put(entry.getKey(), entry.getValue());
        }
    }
}
