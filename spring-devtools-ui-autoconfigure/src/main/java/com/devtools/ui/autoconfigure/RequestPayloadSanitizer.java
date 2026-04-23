package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.policy.DevToolsDataPolicy;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class RequestPayloadSanitizer {

    private RequestPayloadSanitizer() {
    }

    static CapturedBody capture(byte[] content,
                               String contentType,
                               Charset charset,
                               int maxPreviewLength,
                               DevToolsDataPolicy dataPolicy) {
        if (content.length == 0) {
            return new CapturedBody("", false, false);
        }

        if (isBinary(content, contentType)) {
            return new CapturedBody("[binary content omitted, " + content.length + " bytes]", false, true);
        }

        String decoded = new String(content, charset);
        String sanitized = dataPolicy.sanitizePayload(decoded);
        if (!dataPolicy.truncateRequestBodies() || sanitized.length() <= maxPreviewLength) {
            return new CapturedBody(sanitized, false, false);
        }

        int omittedCharacterCount = sanitized.length() - maxPreviewLength;
        return new CapturedBody(
                sanitized.substring(0, maxPreviewLength) + "\n...[truncated " + omittedCharacterCount + " chars]",
                true,
                false
        );
    }

    private static boolean isBinary(byte[] content, String contentType) {
        if (StringUtils.hasText(contentType)) {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (isTextMediaType(mediaType)) {
                return false;
            }
            return true;
        }

        int controlCharacterCount = 0;
        for (byte currentByte : content) {
            int value = currentByte & 0xFF;
            if (value == 0) {
                return true;
            }
            if (isUnexpectedControlCharacter(value)) {
                controlCharacterCount++;
            }
        }

        return controlCharacterCount > Math.max(1, content.length / 10);
    }

    private static boolean isTextMediaType(MediaType mediaType) {
        if ("text".equalsIgnoreCase(mediaType.getType())) {
            return true;
        }

        String subtype = mediaType.getSubtype().toLowerCase(java.util.Locale.ROOT);
        return subtype.contains("json")
                || subtype.contains("xml")
                || subtype.contains("x-www-form-urlencoded")
                || subtype.contains("javascript")
                || subtype.contains("graphql");
    }

    private static boolean isUnexpectedControlCharacter(int value) {
        return value < 0x20 && value != '\n' && value != '\r' && value != '\t';
    }
}
