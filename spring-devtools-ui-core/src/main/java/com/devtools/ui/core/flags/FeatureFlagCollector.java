package com.devtools.ui.core.flags;

import com.devtools.ui.core.model.FeatureFlagDescriptor;
import com.devtools.ui.core.model.FeatureFlagDefinitionDescriptor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeatureFlagCollector {

    private static final List<String> FLAG_PREFIXES = List.of("feature.", "features.", "flag.", "flags.");
    private static final String OVERRIDE_SOURCE_NAME = "spring-devtools-ui-overrides";

    private final ConfigurableEnvironment environment;
    private final FeatureFlagOverrideStore overrideStore;
    private final FeatureFlagDefinitionLookup definitionLookup;

    public FeatureFlagCollector(ConfigurableEnvironment environment,
                                FeatureFlagOverrideStore overrideStore,
                                FeatureFlagDefinitionLookup definitionLookup) {
        this.environment = environment;
        this.overrideStore = overrideStore;
        this.definitionLookup = definitionLookup;
    }

    public List<FeatureFlagDescriptor> collect() {
        Map<String, String> sourceByKey = new LinkedHashMap<>();

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    if (!isPotentialFeatureFlag(propertyName)) {
                        continue;
                    }

                    Boolean resolved = environment.getProperty(propertyName, Boolean.class);
                    if (resolved != null) {
                        sourceByKey.putIfAbsent(propertyName, formatSourceName(propertySource.getName()));
                    }
                }
            }
        }

        overrideStore.asMap().keySet().forEach(key -> sourceByKey.putIfAbsent(key, OVERRIDE_SOURCE_NAME));
        definitionLookup.definitions().forEach(definition -> sourceByKey.putIfAbsent(definition.key(), "saved definition"));

        return sourceByKey.keySet().stream()
                .sorted()
                .map(key -> toDescriptor(key, sourceByKey.get(key)))
                .toList();
    }

    public FeatureFlagDescriptor get(String key) {
        String propertySource = "saved definition";
        for (PropertySource<?> propertySourceCandidate : environment.getPropertySources()) {
            if (propertySourceCandidate.containsProperty(key)) {
                propertySource = formatSourceName(propertySourceCandidate.getName());
                break;
            }
        }
        if (overrideStore.contains(key)) {
            propertySource = OVERRIDE_SOURCE_NAME;
        }
        return toDescriptor(key, propertySource);
    }

    private FeatureFlagDescriptor toDescriptor(String key, String propertySource) {
        FeatureFlagDefinitionDescriptor definition = definitionLookup.find(key);
        return new FeatureFlagDescriptor(
                key,
                Boolean.TRUE.equals(environment.getProperty(key, Boolean.class)),
                propertySource,
                overrideStore.contains(key),
                definition
        );
    }

    private boolean isPotentialFeatureFlag(String propertyName) {
        return FLAG_PREFIXES.stream().anyMatch(propertyName::startsWith);
    }

    private String formatSourceName(String sourceName) {
        int classPathStart = sourceName.indexOf("[");
        int classPathEnd = sourceName.indexOf("]");
        if (sourceName.startsWith("Config resource") && classPathStart >= 0 && classPathEnd > classPathStart) {
            return sourceName.substring(classPathStart + 1, classPathEnd);
        }
        return sourceName;
    }
}
