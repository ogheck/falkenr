package com.devtools.ui.autoconfigure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class HttpTunnelStreamClient implements TunnelStreamClient {

    private final HttpClient httpClient;
    HttpTunnelStreamClient(String relayApiBaseUrl) {
        ExecutorService executor = Executors.newCachedThreadPool();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
    }

    @Override
    public TunnelStreamHandle connect(String streamUrl, TunnelEventListener listener) {
        ExecutorService streamExecutor = Executors.newSingleThreadExecutor();
        Future<?> future = streamExecutor.submit(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(streamUrl))
                    .timeout(Duration.ofMinutes(5))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            try {
                HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    listener.onError("Tunnel stream failed: " + response.statusCode());
                    return;
                }
                listener.onConnected();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    String event = "message";
                    StringBuilder data = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        if (line.isEmpty()) {
                            if (data.length() > 0) {
                                listener.onEvent(event, data.toString().trim());
                                data.setLength(0);
                                event = "message";
                            }
                            continue;
                        }
                        if (line.startsWith("event:")) {
                            event = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            if (!data.isEmpty()) {
                                data.append('\n');
                            }
                            data.append(line.substring(5).trim());
                        }
                    }
                }
                listener.onClosed();
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    listener.onClosed();
                } else {
                    listener.onError("Tunnel stream error: " + exception.getMessage());
                }
            }
        });
        return () -> {
            future.cancel(true);
            streamExecutor.shutdownNow();
        };
    }
}
