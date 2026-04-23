package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DevToolsUiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "test.message=hello-from-test",
                "test.secret=integration-secret",
                "features.checkout=false",
                "spring.devtools.ui.request-limit=100",
                "spring.devtools.ui.log-limit=100",
                "spring.devtools.ui.db-query-limit=100",
                "spring.devtools.ui.max-captured-body-length=12",
                "spring.devtools.ui.history.config-snapshots-file=${java.io.tmpdir}/spring-devtools-ui-config-snapshots-test.json",
                "spring.devtools.ui.feature-flags.definitions-file=${java.io.tmpdir}/spring-devtools-ui-feature-flag-definitions-test.json",
                "management.endpoints.web.exposure.include=health,info",
                "spring.datasource.url=jdbc:h2:mem:devtoolsui;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
        }
)
@AutoConfigureMockMvc
class DevToolsUiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardRouteForwardsToBundledIndex() throws Exception {
        mockMvc.perform(get("/_dev").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/_dev/index.html"));

        mockMvc.perform(get("/_dev/index.html").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<!doctype html>")));
    }

    @Test
    void devDashboardResourcesUseCacheHeadersThatAvoidStaleShells() throws Exception {
        String indexHtml = mockMvc.perform(get("/_dev/index.html").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String assetPath = extractAssetPath(indexHtml);

        mockMvc.perform(get(assetPath).with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=")))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void endpointsApiIncludesApplicationAndDevtoolsRoutes() throws Exception {
        mockMvc.perform(get("/_dev/api/endpoints")
                        .param("limit", "200")
                        .with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].path", hasItems("/hello", "/_dev/api/endpoints", "/echo", "/api/nested/hello")))
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    void configApiIncludesApplicationProperties() throws Exception {
        mockMvc.perform(get("/_dev/api/config")
                        .param("q", "test.")
                        .with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.key == 'test.message')].value", hasItem("hello-from-test")))
                .andExpect(jsonPath("$.items[?(@.key == 'test.secret')].value", hasItem("[masked]")));
    }

    @Test
    void configSnapshotsCanBeCreatedAndCompared() throws Exception {
        String snapshotResponse = mockMvc.perform(post("/_dev/api/config/snapshots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "integration baseline"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("integration baseline"))
                .andExpect(jsonPath("$.properties[?(@.key == 'test.message')].value", hasItem("hello-from-test")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String snapshotId = JsonTestFieldExtractor.extract(snapshotResponse, "snapshotId");

        mockMvc.perform(get("/_dev/api/config/snapshots")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].snapshotId").value(snapshotId))
                .andExpect(jsonPath("$.items[0].label").value("integration baseline"));

        mockMvc.perform(get("/_dev/api/config/compare")
                        .param("snapshotId", snapshotId)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.snapshotId").value(snapshotId))
                .andExpect(jsonPath("$.changedCount").value(0))
                .andExpect(jsonPath("$.unchangedCount").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/_dev/api/config/drift")
                        .param("snapshotId", snapshotId)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.snapshot.snapshotId").value(snapshotId))
                .andExpect(jsonPath("$.drifted").value(false))
                .andExpect(jsonPath("$.totalChanges").value(0));
    }

    @Test
    void featureFlagsApiListsOverridesAndMutatesLocalEnvironment() throws Exception {
        mockMvc.perform(post("/_dev/api/feature-flags/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "features.checkout",
                                  "displayName": "Checkout rollout",
                                  "description": "Controls the staged checkout experience",
                                  "owner": "platform-team",
                                  "tags": ["revenue", "checkout"],
                                  "lifecycle": "active",
                                  "allowOverride": true
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("features.checkout"))
                .andExpect(jsonPath("$.owner").value("platform-team"))
                .andExpect(jsonPath("$.allowOverride").value(true));

        mockMvc.perform(get("/_dev/api/feature-flags")
                        .param("q", "features.checkout")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].key").value("features.checkout"))
                .andExpect(jsonPath("$.items[0].enabled").value(false))
                .andExpect(jsonPath("$.items[0].overridden").value(false))
                .andExpect(jsonPath("$.items[0].definition.owner").value("platform-team"))
                .andExpect(jsonPath("$.items[0].definition.tags", hasItems("checkout", "revenue")));

        mockMvc.perform(get("/feature-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/_dev/api/feature-flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "features.checkout",
                                  "enabled": true
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("features.checkout"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.overridden").value(true));

        mockMvc.perform(get("/feature-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/_dev/api/feature-flags")
                        .param("key", "features.checkout")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/_dev/api/audit-logs")
                        .param("q", "features.checkout")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category").value("feature-flags"))
                .andExpect(jsonPath("$.items[0].detail", containsString("features.checkout")));

        mockMvc.perform(get("/feature-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void featureFlagDefinitionsCanBlockRuntimeOverrides() throws Exception {
        mockMvc.perform(post("/_dev/api/feature-flags/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "features.checkout",
                                  "displayName": "Checkout rollout",
                                  "owner": "platform-team",
                                  "allowOverride": false
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk());

        mockMvc.perform(post("/_dev/api/feature-flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "features.checkout",
                                  "enabled": true
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isConflict());
    }

    @Test
    void requestsApiCapturesMethodHeadersBodyAndStatus() throws Exception {
        mockMvc.perform(post("/_dev/api/session/attach")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "integration-owner",
                                  "allowGuests": false
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk());

        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"spring-devtools-ui\"}")
                        .header("X-Test-Header", "captured"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/_dev/api/requests").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].requestId", containsString("req_")))
                .andExpect(jsonPath("$.items[0].method").value("POST"))
                .andExpect(jsonPath("$.items[0].path").value("/echo"))
                .andExpect(jsonPath("$.items[0].responseStatus").value(200))
                .andExpect(jsonPath("$.items[0].bodyTruncated").value(true))
                .andExpect(jsonPath("$.items[0].binaryBody").value(false))
                .andExpect(jsonPath("$.items[0].body", containsString("...[truncated")))
                .andExpect(jsonPath("$.items[0].headers['X-Test-Header'][0]").value("captured"));

        mockMvc.perform(get("/_dev/api/session/replay")
                        .param("q", "/echo")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category").value("request"))
                .andExpect(jsonPath("$.items[0].title").value("POST /echo"))
                .andExpect(jsonPath("$.items[0].payloadPreview").value("status 200"))
                .andExpect(jsonPath("$.items[0].artifactType").value("request"))
                .andExpect(jsonPath("$.items[0].artifactId", containsString("req_")));

        String requestId = mockMvc.perform(get("/_dev/api/requests").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String capturedRequestId = JsonTestFieldExtractor.extract(requestId, "requestId");

        mockMvc.perform(get("/_dev/api/session/artifacts/request")
                        .param("requestId", capturedRequestId)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(capturedRequestId))
                .andExpect(jsonPath("$.path").value("/echo"));

        mockMvc.perform(post("/_dev/api/session/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "artifactType": "request",
                                  "artifactId": "%s",
                                  "actor": "integration-owner"
                                }
                                """.formatted(capturedRequestId))
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusedArtifactType").value("request"))
                .andExpect(jsonPath("$.focusedArtifactId").value(capturedRequestId))
                .andExpect(jsonPath("$.focusedBy").value("integration-owner"))
                .andExpect(jsonPath("$.focusedAt").isNotEmpty());

        mockMvc.perform(post("/_dev/api/session/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "author": "integration-owner",
                                  "message": "Investigate response mismatch before retrying",
                                  "artifactType": "request",
                                  "artifactId": "%s"
                                }
                                """.formatted(capturedRequestId))
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debugNotes[0].author").value("integration-owner"))
                .andExpect(jsonPath("$.debugNotes[0].message").value("Investigate response mismatch before retrying"))
                .andExpect(jsonPath("$.debugNotes[0].artifactType").value("request"))
                .andExpect(jsonPath("$.debugNotes[0].artifactId").value(capturedRequestId));

        mockMvc.perform(post("/_dev/api/session/recording/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "integration-owner"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingActive").value(true))
                .andExpect(jsonPath("$.currentRecordingId", containsString("recording-")))
                .andExpect(jsonPath("$.recordings[0].active").value(true))
                .andExpect(jsonPath("$.recordings[0].startedBy").value("integration-owner"))
                .andExpect(jsonPath("$.recordings[0].focusedArtifactType").value("request"))
                .andExpect(jsonPath("$.recordings[0].focusedArtifactId").value(capturedRequestId));

        mockMvc.perform(post("/_dev/api/session/recording/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "integration-owner"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordingActive").value(false))
                .andExpect(jsonPath("$.recordingStoppedAt").isNotEmpty())
                .andExpect(jsonPath("$.recordings[0].active").value(false))
                .andExpect(jsonPath("$.recordings[0].stoppedAt").isNotEmpty());
    }

    @Test
    void requestsApiMarksBinaryPayloadsAsOmitted() throws Exception {
        mockMvc.perform(post("/upload")
                        .contentType(MediaType.IMAGE_PNG)
                        .content(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}))
                .andExpect(status().isOk());

        mockMvc.perform(get("/_dev/api/requests")
                        .param("q", "/upload")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].path").value("/upload"))
                .andExpect(jsonPath("$.items[0].binaryBody").value(true))
                .andExpect(jsonPath("$.items[0].body").value(containsString("[binary content omitted")));
    }

    @Test
    void errorReplayReplaysCapturedFiveHundredRequests() throws Exception {
        mockMvc.perform(post("/boom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "secret-value"
                                }
                                """))
                .andExpect(status().isInternalServerError());

        String requestsResponse = mockMvc.perform(get("/_dev/api/requests")
                        .param("q", "/boom")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].responseStatus").value(500))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String capturedRequestId = JsonTestFieldExtractor.extract(requestsResponse, "requestId");

        mockMvc.perform(post("/_dev/api/requests/replay")
                        .param("requestId", capturedRequestId)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(capturedRequestId))
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.path").value("/boom"))
                .andExpect(jsonPath("$.originalStatus").value(500))
                .andExpect(jsonPath("$.replayStatus").value(500))
                .andExpect(jsonPath("$.responseBody", containsString("boom-response")));

        mockMvc.perform(get("/_dev/api/audit-logs")
                        .param("q", capturedRequestId)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category").value("requests"))
                .andExpect(jsonPath("$.items[0].action").value("request.error-replayed"));
    }

    @Test
    void logsApiCapturesApplicationLogs() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/_dev/api/logs").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].message", hasItem("Hello endpoint invoked")))
                .andExpect(jsonPath("$.items[*].stackTrace", hasItem(containsString("IllegalStateException: log-probe"))));
    }

    @Test
    void logsApiSupportsLevelAndLoggerFiltering() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/_dev/api/logs")
                        .param("level", "INFO")
                        .param("logger", "TestController")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].level").value("INFO"))
                .andExpect(jsonPath("$.items[0].logger", containsString("TestController")))
                .andExpect(jsonPath("$.items[0].message").value("Ping endpoint invoked"));
    }

    @Test
    void endpointsApiSupportsServerSideFilteringAndPagination() throws Exception {
        mockMvc.perform(get("/_dev/api/endpoints")
                        .param("q", "echo")
                        .param("offset", "0")
                        .param("limit", "1")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.items[0].path").value("/echo"));
    }

    @Test
    void dependencyGraphApiShowsBeanRelationships() throws Exception {
        mockMvc.perform(get("/_dev/api/dependencies")
                        .param("q", "TestController")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[*].beanType", hasItem("TestController")))
                .andExpect(jsonPath(
                        "$.items[*].dependencies[*]",
                        hasItems(
                                "com.devtools.ui.autoconfigure.DevToolsUiIntegrationTest$JdbcProbe",
                                "com.devtools.ui.autoconfigure.DevToolsUiIntegrationTest$TestLogProbe"
                        )
                ));
    }

    @Test
    void fakeExternalServicesCanBeEnabledAndServeStubResponses() throws Exception {
        mockMvc.perform(get("/_dev/api/fake-services")
                        .param("q", "github")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].serviceId").value("github"))
                .andExpect(jsonPath("$.items[0].enabled").value(false));

        mockMvc.perform(get("/_dev/fake/github/status").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/_dev/api/fake-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "github",
                                  "enabled": true
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value("github"))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/_dev/fake/github/status").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("github"))
                .andExpect(jsonPath("$.status").value("ready"));

        mockMvc.perform(post("/_dev/fake/github/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event": "push"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.service").value("github"))
                .andExpect(jsonPath("$.payload.event").value("push"));

        mockMvc.perform(post("/_dev/api/fake-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "github",
                                  "enabled": true,
                                  "mockResponse": {
                                    "serviceId": "github",
                                    "routeId": "github:POST /_dev/fake/github/webhooks",
                                    "status": 418,
                                    "contentType": "application/problem+json",
                                    "body": "{\\"error\\":\\"teapot\\"}"
                                  }
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mockResponses[0].routeId").isNotEmpty());

        mockMvc.perform(post("/_dev/fake/github/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event": "push"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isIAmATeapot())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(content().json("""
                        {
                          "error": "teapot"
                        }
                        """));
    }

    @Test
    void timeTravelClockOverridesInjectedClock() throws Exception {
        mockMvc.perform(get("/_dev/api/time").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].overridden").value(false));

        mockMvc.perform(get("/time-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overridden").value(false));

        mockMvc.perform(post("/_dev/api/time")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instant": "2026-04-08T16:30:00Z",
                                  "zoneId": "UTC",
                                  "reason": "staging verification",
                                  "durationMinutes": 30
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTime").value("2026-04-08T16:30:00Z"))
                .andExpect(jsonPath("$.zoneId").value("UTC"))
                .andExpect(jsonPath("$.overridden").value(true))
                .andExpect(jsonPath("$.overrideReason").value("staging verification"))
                .andExpect(jsonPath("$.overriddenBy").value("local-operator"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mockMvc.perform(get("/time-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTime").value("2026-04-08T16:30:00Z"))
                .andExpect(jsonPath("$.zoneId").value("UTC"))
                .andExpect(jsonPath("$.overridden").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/_dev/api/time")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/time-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overridden").value(false));
    }

    @Test
    void remoteSessionApiAttachesRotatesAndRevokesLocalSession() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/_dev/api/session")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/_dev/api/session").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].attached").value(false))
                .andExpect(jsonPath("$.items[0].activity").isEmpty())
                .andExpect(jsonPath("$.items[0].replay").isEmpty())
                .andExpect(jsonPath("$.items[0].allowedRoles", hasItems("owner", "viewer")))
                .andExpect(jsonPath("$.items[0].ownerCount").value(0))
                .andExpect(jsonPath("$.items[0].viewerMemberCount").value(0))
                .andExpect(jsonPath("$.items[0].guestMemberCount").value(0))
                .andExpect(jsonPath("$.items[0].recentActors").isEmpty())
                .andExpect(jsonPath("$.items[0].activeMembers").isEmpty())
                .andExpect(jsonPath("$.items[0].activeShareTokens").value(0))
                .andExpect(jsonPath("$.items[0].tokenMode").value("aes-gcm"));

        mockMvc.perform(post("/_dev/api/session/attach")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "pair-debugger",
                                  "allowGuests": true
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attached").value(true))
                .andExpect(jsonPath("$.ownerName").value("pair-debugger"))
                .andExpect(jsonPath("$.accessScope").value("owner-viewer-guest"))
                .andExpect(jsonPath("$.allowedRoles", hasItems("owner", "viewer", "guest")))
                .andExpect(jsonPath("$.relayStatus").value("connected"))
                .andExpect(jsonPath("$.tunnelStatus").value("ready"))
                .andExpect(jsonPath("$.relayHandshakeId").isNotEmpty())
                .andExpect(jsonPath("$.relayConnectionId").isNotEmpty())
                .andExpect(jsonPath("$.relayLeaseId").isNotEmpty())
                .andExpect(jsonPath("$.relayLeaseExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.relayViewerUrl", containsString("/viewer")))
                .andExpect(jsonPath("$.relayTunnelId").isEmpty())
                .andExpect(jsonPath("$.sessionVersion").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.hostedViewStatus").value("unpublished"))
                .andExpect(jsonPath("$.activity[0].eventType").value("relay.connected"))
                .andExpect(jsonPath("$.lastHeartbeatAt").isNotEmpty())
                .andExpect(jsonPath("$.nextHeartbeatAt").isNotEmpty())
                .andExpect(jsonPath("$.ownerTokenPreview").value("[masked]"));

        mockMvc.perform(post("/_dev/api/session/tunnel/open")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relayTunnelId", containsString("tunnel_")))
                .andExpect(jsonPath("$.tunnelStatus").value("streaming"))
                .andExpect(jsonPath("$.tunnelOpenedAt").isNotEmpty())
                .andExpect(jsonPath("$.tunnelClosedAt").isEmpty());

        String shareResponse = mockMvc.perform(post("/_dev/api/session/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "guest"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("guest"))
                .andExpect(jsonPath("$.tokenPreview").value("[masked]"))
                .andExpect(jsonPath("$.shareUrl", containsString("?token=share_")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shareUrl = JsonTestFieldExtractor.extract(shareResponse, "shareUrl");
        String issuedToken = shareUrl.substring(shareUrl.indexOf("?token=") + 7);

        mockMvc.perform(post("/_dev/api/session/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(issuedToken))
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.role").value("guest"))
                .andExpect(jsonPath("$.reason").value("Access granted"))
                .andExpect(jsonPath("$.viewerCount").value(1));

        mockMvc.perform(get("/_dev/api/session/replay")
                        .param("q", "Validation succeeded")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category").value("access"))
                .andExpect(jsonPath("$.items[0].title").value("Validation succeeded"))
                .andExpect(jsonPath("$.items[0].payloadPreview").value("guest joined"));

        mockMvc.perform(get("/_dev/api/session").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].viewerCount").value(1))
                .andExpect(jsonPath("$.items[0].ownerCount").value(1))
                .andExpect(jsonPath("$.items[0].viewerMemberCount").value(0))
                .andExpect(jsonPath("$.items[0].guestMemberCount").value(1))
                .andExpect(jsonPath("$.items[0].recentActors", hasItems("pair-debugger", "guest")))
                .andExpect(jsonPath("$.items[0].activity[0].eventType").value("session.validation_succeeded"))
                .andExpect(jsonPath("$.items[0].replay[0].title").value("Validation succeeded"))
                .andExpect(jsonPath("$.items[0].activeMembers[0].memberId").value("member-1"))
                .andExpect(jsonPath("$.items[0].activeMembers[0].role").value("guest"))
                .andExpect(jsonPath("$.items[0].activeMembers[0].source").value("share-token"))
                .andExpect(jsonPath("$.items[0].workspaceMembers[*].memberId", hasItems("owner", "member-1")))
                .andExpect(jsonPath("$.items[0].workspaceMembers[1].lastAction").value("validated access"))
                .andExpect(jsonPath("$.items[0].recordings").isEmpty())
                .andExpect(jsonPath("$.items[0].recordingActive").value(false));

        mockMvc.perform(post("/_dev/api/session/heartbeat")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relayStatus").value("connected"))
                .andExpect(jsonPath("$.tunnelStatus").value("healthy"))
                .andExpect(jsonPath("$.relayTunnelId", containsString("tunnel_")))
                .andExpect(jsonPath("$.relayLeaseId").isNotEmpty())
                .andExpect(jsonPath("$.relayLeaseExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.lastHeartbeatAt").isNotEmpty())
                .andExpect(jsonPath("$.nextHeartbeatAt").isNotEmpty())
                .andExpect(jsonPath("$.reconnectAt").isEmpty());

        mockMvc.perform(post("/_dev/api/session/sync")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncStatus").value("synced"))
                .andExpect(jsonPath("$.lastSyncId", containsString("sync_")))
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.publishedSessionVersion").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.hostedViewStatus").value("published"))
                .andExpect(jsonPath("$.lastPublishedAt").isNotEmpty());

        mockMvc.perform(get("/_dev/api/session/hosted-view").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.durableState").value("published"))
                .andExpect(jsonPath("$.viewId", containsString("view_")))
                .andExpect(jsonPath("$.publishedVersion").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.members[*].memberId", hasItems("owner", "member-1")))
                .andExpect(jsonPath("$.members[1].role").value("guest"));

        mockMvc.perform(post("/_dev/api/session/tunnel/close")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relayTunnelId").isEmpty())
                .andExpect(jsonPath("$.tunnelStatus").value("closed"))
                .andExpect(jsonPath("$.tunnelOpenedAt").isNotEmpty())
                .andExpect(jsonPath("$.tunnelClosedAt").isNotEmpty());

        mockMvc.perform(get("/_dev/api/session/hosted-history").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].viewId", containsString("view_")))
                .andExpect(jsonPath("$.items[0].syncId", containsString("sync_")))
                .andExpect(jsonPath("$.items[0].members.length()").value(2));

        mockMvc.perform(post("/_dev/api/session/hosted-view/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": "viewer-2",
                                  "role": "viewer",
                                  "source": "relay-viewer",
                                  "actor": "pair-debugger"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[*].memberId", hasItems("owner", "member-1", "viewer-2")))
                .andExpect(jsonPath("$.members[2].source").value("relay-viewer"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/_dev/api/session/hosted-view/members")
                        .param("memberId", "viewer-2")
                        .param("actor", "pair-debugger")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[*].memberId", hasItems("owner", "member-1")))
                .andExpect(jsonPath("$.members[*].memberId", org.hamcrest.Matchers.not(hasItem("viewer-2"))));

        mockMvc.perform(get("/_dev/api/session/hosted-history").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].members.length()").value(2));

        mockMvc.perform(post("/_dev/api/session/token")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attached").value(true))
                .andExpect(jsonPath("$.ownerTokenPreview").exists());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/_dev/api/session")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/_dev/api/session").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].attached").value(false))
                .andExpect(jsonPath("$.items[0].ownerName").value("local-developer"));
    }

    @Test
    void actuatorAndErrorRoutesAreDiscoverableThroughEndpointExplorer() throws Exception {
        mockMvc.perform(get("/_dev/api/endpoints")
                        .param("limit", "200")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].path", hasItems("/error", "/actuator/health")));
    }

    static final class JsonTestFieldExtractor {

        static String extract(String json, String field) {
            Pattern pattern = Pattern.compile("\"" + field + "\":\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(json);
            if (!matcher.find()) {
                throw new IllegalStateException("Missing field " + field + " in " + json);
            }
            return matcher.group(1);
        }
    }

    @Test
    void jobsApiDiscoversScheduledMethods() throws Exception {
        mockMvc.perform(get("/_dev/api/jobs")
                        .param("q", "heartbeat")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].beanType").value("ScheduledProbe"))
                .andExpect(jsonPath("$.items[0].methodName").value("heartbeat"))
                .andExpect(jsonPath("$.items[0].triggerType").value("fixedDelay"))
                .andExpect(jsonPath("$.items[0].expression").value("30000ms"));
    }

    @Test
    void dbQueriesApiCapturesJdbcStatements() throws Exception {
        mockMvc.perform(get("/db/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(get("/_dev/api/db-queries")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[*].statementType", hasItem("select")))
                .andExpect(jsonPath("$.items[*].dataSource", hasItem("dataSource")))
                .andExpect(jsonPath("$.items[*].sql", hasItem(containsString("select id, name from users"))));
    }

    @Test
    void dbQueriesApiMasksSensitiveSqlValues() throws Exception {
        mockMvc.perform(get("/db/secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true));

        mockMvc.perform(get("/_dev/api/db-queries")
                        .param("q", "api_keys")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].sql", containsString("'[masked]'")))
                .andExpect(jsonPath("$.items[0].sql", containsString("[masked]")))
                .andExpect(jsonPath("$.items[0].sql", org.hamcrest.Matchers.not(containsString("super-secret-token"))));
    }

    @Test
    void webhookSimulatorDiscoversTargetsAndDeliversPayloads() throws Exception {
        mockMvc.perform(get("/_dev/api/webhooks/targets")
                        .param("q", "/webhooks/github")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].path").value("/webhooks/github"))
                .andExpect(jsonPath("$.items[0].method").value("POST"));

        mockMvc.perform(post("/_dev/api/webhooks/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": "/webhooks/github",
                                  "body": "{\\"event\\":\\"push\\",\\"repository\\":\\"spring-devtools-ui\\"}",
                                  "headers": {
                                    "X-GitHub-Event": "push"
                                  }
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.path").value("/webhooks/github"))
                .andExpect(jsonPath("$.responseBody", containsString("accepted")));
    }

    @Test
    void nestedServletMappingsAreCollectedAsFullPaths() throws Exception {
        mockMvc.perform(get("/_dev/api/endpoints")
                        .param("q", "/api/nested/hello")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].path").value("/api/nested/hello"));
    }

    @Test
    void remoteAddressesAreBlockedFromDashboard() throws Exception {
        mockMvc.perform(get("/_dev").with(request -> {
                    request.setRemoteAddr("203.0.113.10");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @Test
    void spoofedForwardedAddressesAreBlockedFromDashboard() throws Exception {
        mockMvc.perform(get("/_dev")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableScheduling
    @Import({TestController.class, NestedController.class, TestLogProbe.class, ScheduledProbe.class, JdbcProbe.class})
    static class TestApplication {

    }

    @RestController
    static class TestController {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TestController.class);

        private final TestLogProbe testLogProbe;
        private final JdbcProbe jdbcProbe;
        private final Environment environment;
        private final Clock clock;

        TestController(TestLogProbe testLogProbe, JdbcProbe jdbcProbe, Environment environment, Clock clock) {
            this.testLogProbe = testLogProbe;
            this.jdbcProbe = jdbcProbe;
            this.environment = environment;
            this.clock = clock;
        }

        @GetMapping("/hello")
        Map<String, String> hello() {
            testLogProbe.logHello();
            return Map.of("message", "hello");
        }

        @GetMapping("/ping")
        Map<String, String> ping() {
            log.info("Ping endpoint invoked");
            return Map.of("message", "pong");
        }

        @PostMapping("/echo")
        Map<String, Object> echo(@RequestBody Map<String, Object> payload) {
            return Map.of("received", payload);
        }

        @PostMapping("/upload")
        Map<String, Object> upload(@RequestBody byte[] payload) {
            return Map.of("bytes", payload.length);
        }

        @PostMapping("/boom")
        org.springframework.http.ResponseEntity<Map<String, Object>> boom(@RequestBody(required = false) Map<String, Object> payload) {
            return org.springframework.http.ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "boom-response",
                            "token", payload == null ? "missing" : payload.getOrDefault("token", "missing")
                    ));
        }

        @GetMapping("/db/users")
        Map<String, Object> dbUsers() {
            return Map.of("count", jdbcProbe.fetchUsers());
        }

        @GetMapping("/db/secret")
        Map<String, Object> dbSecret() {
            jdbcProbe.insertSensitiveRow();
            return Map.of("created", true);
        }

        @GetMapping("/feature-state")
        Map<String, Object> featureState() {
            return Map.of("enabled", environment.getProperty("features.checkout", Boolean.class, false));
        }

        @GetMapping("/time-state")
        Map<String, Object> timeState() {
            return Map.of(
                    "currentTime", clock.instant().toString(),
                    "zoneId", clock.getZone().getId(),
                    "overridden", clock instanceof MutableDevToolsClock mutableDevToolsClock && mutableDevToolsClock.isOverridden()
            );
        }

        @PostMapping("/webhooks/github")
        Map<String, Object> githubWebhook(@RequestBody Map<String, Object> payload) {
            return Map.of("accepted", true, "payload", payload);
        }
    }

    @RestController
    @RequestMapping("/api/nested")
    static class NestedController {

        @GetMapping("/hello")
        Map<String, String> hello() {
            return Map.of("message", "nested");
        }
    }

    @Component
    static class TestLogProbe {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TestLogProbe.class);

        void logHello() {
            log.error("Hello endpoint invoked", new IllegalStateException("log-probe"));
        }
    }

    @Component
    static class ScheduledProbe {

        @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000L)
        void heartbeat() {
        }
    }

    @Component
    static class JdbcProbe {

        private final JdbcTemplate jdbcTemplate;

        JdbcProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @jakarta.annotation.PostConstruct
        void initialize() {
            jdbcTemplate.execute("create table if not exists users (id int primary key, name varchar(255))");
            jdbcTemplate.update("delete from users");
            jdbcTemplate.update("insert into users (id, name) values (?, ?)", 1, "Ada");
            jdbcTemplate.update("insert into users (id, name) values (?, ?)", 2, "Grace");
        }

        int fetchUsers() {
            return jdbcTemplate.query("select id, name from users order by id", rs -> {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                return count;
            });
        }

        void insertSensitiveRow() {
            jdbcTemplate.execute("create table if not exists api_keys (id int primary key, token varchar(255), account_id bigint)");
            jdbcTemplate.update("delete from api_keys");
            jdbcTemplate.execute("insert into api_keys (id, token, account_id) values (1, 'super-secret-token', 987654321)");
        }
    }

    private static String extractAssetPath(String indexHtml) {
        Matcher matcher = Pattern.compile("\"(/_dev/assets/[^\"]+)\"").matcher(indexHtml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Expected an asset path in bundled index.html");
    }
}
