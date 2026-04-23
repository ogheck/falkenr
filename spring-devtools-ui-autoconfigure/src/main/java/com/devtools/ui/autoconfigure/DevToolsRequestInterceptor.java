package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.policy.DevToolsDataPolicy;
import com.devtools.ui.core.requests.RequestCaptureStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class DevToolsRequestInterceptor implements HandlerInterceptor {

    private final boolean enabled;
    private final RequestCaptureStore requestCaptureStore;
    private final RemoteSessionService remoteSessionService;
    private final int maxCapturedBodyLength;
    private final DevToolsUiProperties.RequestSamplingSettings requestSamplingSettings;
    private final DevToolsDataPolicy dataPolicy;

    DevToolsRequestInterceptor(boolean enabled,
                               RequestCaptureStore requestCaptureStore,
                               RemoteSessionService remoteSessionService,
                               int maxCapturedBodyLength,
                               DevToolsUiProperties.RequestSamplingSettings requestSamplingSettings,
                               DevToolsDataPolicy dataPolicy) {
        this.enabled = enabled;
        this.requestCaptureStore = requestCaptureStore;
        this.remoteSessionService = remoteSessionService;
        this.maxCapturedBodyLength = maxCapturedBodyLength;
        this.requestSamplingSettings = requestSamplingSettings;
        this.dataPolicy = dataPolicy;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        if (!enabled) {
            return;
        }
        if (request.getRequestURI().startsWith(DevToolsUiConstants.ROOT_PATH)) {
            return;
        }
        if (!dataPolicy.shouldCaptureRequest(request.getRequestURI())) {
            return;
        }
        if (!shouldCapture(request, response)) {
            return;
        }

        CapturedBody capturedBody = extractBody(request);

        CapturedRequest capturedRequest = new CapturedRequest(
                "req_" + UUID.randomUUID(),
                request.getMethod(),
                request.getRequestURI(),
                collectHeaders(request),
                capturedBody.value(),
                capturedBody.truncated(),
                capturedBody.binary(),
                Instant.now(),
                response.getStatus()
        );
        requestCaptureStore.append(capturedRequest);
        remoteSessionService.recordRequestReplay(capturedRequest);
    }

    private boolean shouldCapture(HttpServletRequest request, HttpServletResponse response) {
        if (!requestSamplingSettings.isEnabled()) {
            return true;
        }
        if (requestSamplingSettings.isAlwaysCaptureErrors() && response.getStatus() >= 500) {
            return true;
        }
        int percentage = requestSamplingSettings.getPercentage();
        if (percentage <= 0) {
            return false;
        }
        if (percentage >= 100) {
            return true;
        }
        int bucket = Math.floorMod((request.getMethod() + ":" + request.getRequestURI()).hashCode(), 100);
        return bucket < percentage;
    }

    private Map<String, List<String>> collectHeaders(HttpServletRequest request) {
        if (!dataPolicy.captureRequestHeaders()) {
            return Map.of();
        }
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return Map.of();
        }

        Map<String, List<String>> headers = new LinkedHashMap<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, Collections.list(request.getHeaders(headerName)));
        }
        return dataPolicy.sanitizeHeaders(headers);
    }

    private CapturedBody extractBody(HttpServletRequest request) {
        if (!dataPolicy.captureRequestBodies()) {
            return new CapturedBody("", false, false);
        }
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null || wrapper.getContentAsByteArray().length == 0) {
            return new CapturedBody("", false, false);
        }

        Charset charset = wrapper.getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(wrapper.getCharacterEncoding());

        return RequestPayloadSanitizer.capture(
                wrapper.getContentAsByteArray(),
                wrapper.getContentType(),
                charset,
                maxCapturedBodyLength,
                dataPolicy
        );
    }
}
