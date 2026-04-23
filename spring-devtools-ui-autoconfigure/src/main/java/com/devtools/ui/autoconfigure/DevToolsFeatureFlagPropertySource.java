package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.flags.FeatureFlagOverrideStore;
import org.springframework.core.env.MapPropertySource;

class DevToolsFeatureFlagPropertySource extends MapPropertySource {

    static final String NAME = "spring-devtools-ui-overrides";

    DevToolsFeatureFlagPropertySource(FeatureFlagOverrideStore overrideStore) {
        super(NAME, overrideStore.asMap());
    }
}
