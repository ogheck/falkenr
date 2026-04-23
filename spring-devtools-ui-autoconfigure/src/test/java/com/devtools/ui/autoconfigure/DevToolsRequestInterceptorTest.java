package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.requests.RequestCaptureStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DevToolsRequestInterceptorTest {

    private DefaultDevToolsDataPolicy defaultPolicy() {
        return new DefaultDevToolsDataPolicy(new DevToolsUiProperties.PolicySettings());
    }

    private RemoteSessionService remoteSessionService() {
        DevToolsUiProperties.RemoteSettings settings = new DevToolsUiProperties.RemoteSettings();
        return new RemoteSessionService(
                new LocalAgentSessionStore(java.time.Clock.systemUTC(), settings, new DevToolsUiProperties.SecretsSettings(), defaultPolicy()),
                new LocalRelayClient(settings),
                new RelayTokenCodec(java.time.Clock.systemUTC()),
                new InMemoryHostedRelaySessionStore(),
                new NoOpTunnelStreamClient()
        );
    }

    private DevToolsUiProperties.RequestSamplingSettings noSampling() {
        return new DevToolsUiProperties.RequestSamplingSettings();
    }

    @Test
    void afterCompletionCapturesHeadersBodyAndStatus() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 256, noSampling(), defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submit");
        request.addHeader("X-Test-Header", "captured");
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader("X-Session-Token", "session-secret");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"feature\":\"collector-test\"}".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        interceptor.afterCompletion(wrapper, response, new Object(), null);

        assertThat(store.snapshot()).singleElement().satisfies(capturedRequest -> {
            assertThat(capturedRequest.method()).isEqualTo("POST");
            assertThat(capturedRequest.requestId()).startsWith("req_");
            assertThat(capturedRequest.path()).isEqualTo("/submit");
            assertThat(capturedRequest.responseStatus()).isEqualTo(201);
            assertThat(capturedRequest.body()).isEqualTo("{\"feature\":\"collector-test\"}");
            assertThat(capturedRequest.bodyTruncated()).isFalse();
            assertThat(capturedRequest.binaryBody()).isFalse();
            assertThat(capturedRequest.headers()).containsKey("X-Test-Header");
            assertThat(capturedRequest.headers().get("Authorization")).containsExactly("[masked]");
            assertThat(capturedRequest.headers().get("X-Session-Token")).containsExactly("[masked]");
        });
    }

    @Test
    void afterCompletionMasksSensitiveBodyFieldsAndTestAccounts() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 512, noSampling(), defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submit");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("""
                {"email":"qa-user@example.com","password":"super-secret","sessionId":"abc123","owner":"real.user@example.com"}
                """.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();

        interceptor.afterCompletion(wrapper, new MockHttpServletResponse(), new Object(), null);

        assertThat(store.snapshot()).singleElement().satisfies(capturedRequest -> {
            assertThat(capturedRequest.body()).contains("\"email\":\"[masked]\"");
            assertThat(capturedRequest.body()).contains("\"password\":\"[masked]\"");
            assertThat(capturedRequest.body()).contains("\"sessionId\":\"[masked]\"");
            assertThat(capturedRequest.body()).contains("real.user@example.com");
        });
    }

    @Test
    void afterCompletionIgnoresDevtoolsRequests() {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 256, noSampling(), defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/_dev/api/logs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void afterCompletionTruncatesLargeTextBodies() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 8, noSampling(), defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submit");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"payload\":\"very-long\"}".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();

        interceptor.afterCompletion(wrapper, new MockHttpServletResponse(), new Object(), null);

        assertThat(store.snapshot()).singleElement().satisfies(capturedRequest -> {
            assertThat(capturedRequest.bodyTruncated()).isTrue();
            assertThat(capturedRequest.body()).contains("...[truncated");
        });
    }

    @Test
    void afterCompletionOmitsBinaryBodies() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 256, noSampling(), defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
        request.setContentType("application/octet-stream");
        request.setContent(new byte[]{0x01, 0x02, 0x00, 0x03});
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();

        interceptor.afterCompletion(wrapper, new MockHttpServletResponse(), new Object(), null);

        assertThat(store.snapshot()).singleElement().satisfies(capturedRequest -> {
            assertThat(capturedRequest.binaryBody()).isTrue();
            assertThat(capturedRequest.body()).contains("[binary content omitted");
        });
    }

    @Test
    void afterCompletionTreatsImagePayloadsAsBinaryEvenWithoutControlCharacters() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 256, noSampling(), defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/image");
        request.setContentType("image/png");
        request.setContent(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();

        interceptor.afterCompletion(wrapper, new MockHttpServletResponse(), new Object(), null);

        assertThat(store.snapshot()).singleElement().satisfies(capturedRequest -> {
            assertThat(capturedRequest.binaryBody()).isTrue();
            assertThat(capturedRequest.body()).contains("[binary content omitted, 4 bytes]");
        });
    }

    @Test
    void afterCompletionSkipsCaptureWhenRequestsFeatureIsDisabled() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(false, store, remoteSessionService(), 256, noSampling(), defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/submit");
        request.setContentType("application/json");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent("{\"feature\":\"disabled\"}".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        wrapper.getInputStream().readAllBytes();

        interceptor.afterCompletion(wrapper, new MockHttpServletResponse(), new Object(), null);

        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void afterCompletionSkipsRequestsWhenSamplingRejectsThem() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsUiProperties.RequestSamplingSettings sampling = new DevToolsUiProperties.RequestSamplingSettings();
        sampling.setEnabled(true);
        sampling.setPercentage(0);
        sampling.setAlwaysCaptureErrors(false);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 256, sampling, defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sampled-out");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void afterCompletionAlwaysCapturesErrorsWhenSamplingIsEnabled() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsUiProperties.RequestSamplingSettings sampling = new DevToolsUiProperties.RequestSamplingSettings();
        sampling.setEnabled(true);
        sampling.setPercentage(0);
        sampling.setAlwaysCaptureErrors(true);
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(true, store, remoteSessionService(), 256, sampling, defaultPolicy());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/server-error");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(store.snapshot()).singleElement().satisfies(capturedRequest -> {
            assertThat(capturedRequest.path()).isEqualTo("/server-error");
            assertThat(capturedRequest.responseStatus()).isEqualTo(500);
        });
    }

    @Test
    void policyCanDisableBodyCaptureAndExcludePaths() throws Exception {
        RequestCaptureStore store = new RequestCaptureStore(10);
        DevToolsUiProperties.PolicySettings policySettings = new DevToolsUiProperties.PolicySettings();
        policySettings.setCaptureRequestBodies(false);
        policySettings.setExcludedPaths(java.util.List.of("/skip", "/internal/**"));
        DevToolsRequestInterceptor interceptor = new DevToolsRequestInterceptor(
                true,
                store,
                remoteSessionService(),
                256,
                noSampling(),
                new DefaultDevToolsDataPolicy(policySettings)
        );

        MockHttpServletRequest skipped = new MockHttpServletRequest("POST", "/internal/health");
        skipped.setContentType("application/json");
        skipped.setCharacterEncoding(StandardCharsets.UTF_8.name());
        skipped.setContent("{\"secret\":\"ignored\"}".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper skippedWrapper = new ContentCachingRequestWrapper(skipped);
        skippedWrapper.getInputStream().readAllBytes();
        interceptor.afterCompletion(skippedWrapper, new MockHttpServletResponse(), new Object(), null);

        MockHttpServletRequest captured = new MockHttpServletRequest("POST", "/submit");
        captured.setContentType("application/json");
        captured.setCharacterEncoding(StandardCharsets.UTF_8.name());
        captured.setContent("{\"secret\":\"kept-out\"}".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper capturedWrapper = new ContentCachingRequestWrapper(captured);
        capturedWrapper.getInputStream().readAllBytes();
        interceptor.afterCompletion(capturedWrapper, new MockHttpServletResponse(), new Object(), null);

        assertThat(store.snapshot()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/submit");
            assertThat(request.body()).isEmpty();
            assertThat(request.bodyTruncated()).isFalse();
        });
    }
}
