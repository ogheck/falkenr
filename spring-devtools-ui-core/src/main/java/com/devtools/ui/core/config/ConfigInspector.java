package com.devtools.ui.core.config;

import com.devtools.ui.core.model.ConfigPropertyDescriptor;
import com.devtools.ui.core.policy.DevToolsDataPolicy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConfigInspector {

    private final ConfigurableEnvironment environment;
    private final DevToolsDataPolicy dataPolicy;

    public ConfigInspector(ConfigurableEnvironment environment, DevToolsDataPolicy dataPolicy) {
        this.environment = environment;
        this.dataPolicy = dataPolicy;
    }

    public List<ConfigPropertyDescriptor> inspect() {
        Map<String, String> sourceByKey = new LinkedHashMap<>();

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    sourceByKey.putIfAbsent(propertyName, formatSourceName(propertySource.getName()));
                }
            }
        }

        return sourceByKey.keySet().stream()
                .sorted()
                .map(key -> new ConfigPropertyDescriptor(
                        key,
                        dataPolicy.sanitizeConfigValue(key, Objects.toString(environment.getProperty(key), "")),
                        sourceByKey.get(key)
                ))
                .toList();
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
