package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.CapturedRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFileRequestCapturePersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void retainsOnlyConfiguredRequestCount() {
        JsonFileRequestCapturePersistence persistence = new JsonFileRequestCapturePersistence(
                tempDir.resolve("request-history.json"),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                2
        );

        persistence.save(List.of(
                request("req-1", "/one"),
                request("req-2", "/two"),
                request("req-3", "/three")
        ));

        List<String> requestIds = persistence.load().stream().map(CapturedRequest::requestId).toList();
        assertThat(requestIds).containsExactly("req-2", "req-3");
    }

    private CapturedRequest request(String requestId, String path) {
        return new CapturedRequest(
                requestId,
                "GET",
                path,
                Map.of(),
                "",
                false,
                false,
                Instant.parse("2026-04-09T12:00:00Z"),
                200
        );
    }
}
