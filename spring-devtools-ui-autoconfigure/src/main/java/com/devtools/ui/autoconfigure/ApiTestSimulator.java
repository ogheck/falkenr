package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.policy.DevToolsDataPolicy;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

class ApiTestSimulator {

    private final ApplicationContext applicationContext;
    private final RestClient restClient;
    private final DevToolsDataPolicy dataPolicy;

    ApiTestSimulator(ApplicationContext applicationContext, DevToolsDataPolicy dataPolicy) {
        this.applicationContext = applicationContext;
        this.dataPolicy = dataPolicy;
        this.restClient = RestClient.builder().build();
    }

    ApiTestResult deliver(ApiTestRequest request) {
        String method = request.method() == null || request.method().isBlank()
                ? "GET"
                : request.method().trim().toUpperCase(Locale.ROOT);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (request.headers() != null) {
            request.headers().forEach(headers::set);
        }
        String body = request.body() == null ? "" : request.body();
        ApiTestResult response = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(localUrl(request.path()))
                .headers(httpHeaders -> copyHeaders(headers, httpHeaders))
                .body(body)
                .exchange((httpRequest, httpResponse) -> new ApiTestResult(
                        method,
                        normalizePath(request.path()),
                        httpResponse.getStatusCode().value(),
                        dataPolicy.sanitizePayload(readBody(httpResponse.getHeaders(), httpResponse.getBody()))
                ));
        return response;
    }

    private String localUrl(String path) {
        if (!(applicationContext instanceof WebServerApplicationContext webServerApplicationContext)) {
            throw new IllegalStateException("API test simulator requires a running web server application context");
        }
        return "http://127.0.0.1:" + webServerApplicationContext.getWebServer().getPort() + normalizePath(path);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private void copyHeaders(HttpHeaders source, HttpHeaders target) {
        for (Map.Entry<String, java.util.List<String>> entry : source.entrySet()) {
            target.put(entry.getKey(), entry.getValue());
        }
    }

    private String readBody(HttpHeaders headers, java.io.InputStream body) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        MediaType contentType = headers.getContentType();
        if (contentType != null && contentType.getCharset() != null) {
            charset = contentType.getCharset();
        }
        return StreamUtils.copyToString(body, charset);
    }
}
