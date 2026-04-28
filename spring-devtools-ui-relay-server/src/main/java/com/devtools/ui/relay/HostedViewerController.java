package com.devtools.ui.relay;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;

@RestController
class HostedViewerController {

    private static final String ACCOUNT_SESSION = "__ACCOUNT_SESSION__";
    private static final String SESSION_TOKEN = "__SESSION_ID__";
    private static final String VIEWER_SESSION = "__VIEWER_SESSION__";

    private final InMemoryRelaySessionStore sessionStore;

    HostedViewerController(InMemoryRelaySessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping(path = {"/", "/early-access", "/pricing"}, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> homepage() throws IOException {
        return ResponseEntity.ok(readResourceOrFallback("site/index.html", fallbackHomepage()));
    }

    @GetMapping(path = "/assets/{fileName:.+}")
    ResponseEntity<byte[]> homepageAsset(@PathVariable String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource("site/assets/" + fileName);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        MediaType contentType = mediaTypeFor(fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(contentType)
                .body(resource.getInputStream().readAllBytes());
    }

    @GetMapping(path = "/logo.png")
    ResponseEntity<byte[]> homepageLogo() throws IOException {
        ClassPathResource resource = new ClassPathResource("site/logo.png");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.IMAGE_PNG)
                .body(resource.getInputStream().readAllBytes());
    }

    @GetMapping(path = {"/view/{sessionId}", "/s/{sessionId}"}, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> hostedViewer(@PathVariable String sessionId,
                                        @RequestParam(name = "token", required = false) String token,
                                        @RequestParam(name = "accountSession", required = false) String accountSession) throws IOException {
        RelayViewerSessionResponse viewerSession = sessionStore.createViewerSession(
                sessionId,
                new RelayViewerSessionRequest(token, accountSession, "share-link-viewer")
        );
        String html = readResource("relay-viewer.html")
                .replace(SESSION_TOKEN, escapeJs(sessionId))
                .replace(VIEWER_SESSION, escapeJs(viewerSession.viewerSessionToken()));
        return ResponseEntity.ok(html);
    }

    @GetMapping(path = "/app", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> hostedDashboard(@RequestParam(name = "accountSession", required = false) String accountSession,
                                           HttpServletRequest request) throws IOException {
        String resolved = accountSession;
        if (resolved == null || resolved.isBlank()) {
            Object sessionToken = request.getSession(false) == null ? null : request.getSession(false).getAttribute(RelayWebLoginSuccessHandler.ACCOUNT_SESSION_ATTR);
            if (sessionToken instanceof String token && !token.isBlank()) {
                resolved = token;
            }
        }
        String html = readResource("relay-dashboard.html")
                .replace(ACCOUNT_SESSION, escapeJs(resolved == null ? "" : resolved));
        return ResponseEntity.ok(html);
    }

    private String readResource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private String readResourceOrFallback(String path, String fallback) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return fallback;
        }
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private String fallbackHomepage() {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>Falkenr</title>
                  </head>
                  <body>
                    <main>
                      <h1>Falkenr</h1>
                      <p>Zero-config Spring Boot developer dashboard for endpoints, requests, config, and logs.</p>
                      <p><a href="/app">Open hosted dashboard</a></p>
                    </main>
                  </body>
                </html>
                """;
    }

    private MediaType mediaTypeFor(String fileName) {
        if (fileName.endsWith(".js")) {
            return MediaType.valueOf("text/javascript");
        }
        if (fileName.endsWith(".css")) {
            return MediaType.valueOf("text/css");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String escapeJs(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }
}
