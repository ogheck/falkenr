package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.requests.RequestCapturePersistence;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class JsonFileRequestCapturePersistence implements RequestCapturePersistence {

    private final Path persistenceFile;
    private final ObjectMapper objectMapper;
    private final int maxPersistedRequests;

    JsonFileRequestCapturePersistence(Path persistenceFile, ObjectMapper objectMapper, int maxPersistedRequests) {
        this.persistenceFile = persistenceFile;
        this.objectMapper = objectMapper;
        this.maxPersistedRequests = Math.max(1, maxPersistedRequests);
    }

    @Override
    public List<CapturedRequest> load() {
        if (!Files.exists(persistenceFile)) {
            return List.of();
        }
        try {
            List<CapturedRequest> requests = objectMapper.readValue(Files.readAllBytes(persistenceFile), new TypeReference<>() {
            });
            return trimToRetention(requests);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load persisted request history from " + persistenceFile, exception);
        }
    }

    @Override
    public void save(List<CapturedRequest> requests) {
        try {
            if (persistenceFile.getParent() != null) {
                Files.createDirectories(persistenceFile.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(persistenceFile.toFile(), trimToRetention(requests));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist request history to " + persistenceFile, exception);
        }
    }

    private List<CapturedRequest> trimToRetention(List<CapturedRequest> requests) {
        if (requests.size() <= maxPersistedRequests) {
            return List.copyOf(requests);
        }
        return List.copyOf(requests.subList(requests.size() - maxPersistedRequests, requests.size()));
    }
}
