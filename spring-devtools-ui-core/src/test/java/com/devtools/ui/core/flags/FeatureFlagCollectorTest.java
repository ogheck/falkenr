package com.devtools.ui.core.flags;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagCollectorTest {

    @Test
    void collectReturnsBooleanFlagsAndAppliesOverrides() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "features.checkout", "false",
                "feature.audit", "true",
                "service.url", "http://localhost"
        )));

        FeatureFlagOverrideStore store = new FeatureFlagOverrideStore();
        store.set("features.checkout", true);
        environment.getPropertySources().addFirst(new MapPropertySource("spring-devtools-ui-overrides", store.asMap()));

        FeatureFlagCollector collector = new FeatureFlagCollector(environment, store, new FeatureFlagDefinitionLookup() {
            @Override
            public List<com.devtools.ui.core.model.FeatureFlagDefinitionDescriptor> definitions() {
                return List.of();
            }

            @Override
            public com.devtools.ui.core.model.FeatureFlagDefinitionDescriptor find(String key) {
                return null;
            }
        });

        assertThat(collector.collect())
                .extracting(flag -> flag.key() + ":" + flag.enabled() + ":" + flag.overridden())
                .containsExactly("feature.audit:true:false", "features.checkout:true:true");
    }
}
