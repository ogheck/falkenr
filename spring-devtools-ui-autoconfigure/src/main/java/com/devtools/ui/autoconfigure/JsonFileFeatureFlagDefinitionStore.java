package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.flags.FeatureFlagDefinitionLookup;
import com.devtools.ui.core.model.FeatureFlagDefinitionDescriptor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class JsonFileFeatureFlagDefinitionStore implements FeatureFlagDefinitionLookup {

    private static final TypeReference<List<FeatureFlagDefinitionDescriptor>> DEFINITION_LIST = new TypeReference<>() {
    };

    private final Path file;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean persist;
    private final Map<String, FeatureFlagDefinitionDescriptor> definitionsByKey;

    JsonFileFeatureFlagDefinitionStore(Path file, ObjectMapper objectMapper, Clock clock, boolean persist) {
        this.file = file;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.persist = persist;
        this.definitionsByKey = new LinkedHashMap<>();
        loadDefinitions().forEach(definition -> definitionsByKey.put(definition.key(), definition));
    }

    @Override
    public synchronized List<FeatureFlagDefinitionDescriptor> definitions() {
        return definitionsByKey.values().stream()
                .sorted(Comparator.comparing(FeatureFlagDefinitionDescriptor::key))
                .toList();
    }

    @Override
    public synchronized FeatureFlagDefinitionDescriptor find(String key) {
        return definitionsByKey.get(key);
    }

    synchronized FeatureFlagDefinitionDescriptor save(FeatureFlagDefinitionUpdateRequest request, String actor) {
        String key = normalizeRequired(request.key(), "key");
        FeatureFlagDefinitionDescriptor definition = new FeatureFlagDefinitionDescriptor(
                key,
                normalizeOptional(request.displayName()),
                normalizeOptional(request.description()),
                normalizeOptional(request.owner()),
                normalizeTags(request.tags()),
                normalizeLifecycle(request.lifecycle()),
                request.allowOverride() == null || request.allowOverride(),
                persist,
                Instant.now(clock).toString(),
                actor
        );
        definitionsByKey.put(key, definition);
        persistDefinitions();
        return definition;
    }

    synchronized void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (definitionsByKey.remove(key.trim()) != null) {
            persistDefinitions();
        }
    }

    private List<FeatureFlagDefinitionDescriptor> loadDefinitions() {
        if (!persist || !Files.exists(file)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(file.toFile(), DEFINITION_LIST);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load feature flag definitions from " + file, exception);
        }
    }

    private void persistDefinitions() {
        if (!persist) {
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ArrayList<>(definitions()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist feature flag definitions to " + file, exception);
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Feature flag " + fieldName + " is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .map(this::normalizeOptional)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private String normalizeLifecycle(String lifecycle) {
        String normalized = normalizeOptional(lifecycle);
        return normalized.isBlank() ? "active" : normalized.toLowerCase(Locale.ROOT);
    }
}
