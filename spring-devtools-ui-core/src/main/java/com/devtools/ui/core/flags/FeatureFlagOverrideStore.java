package com.devtools.ui.core.flags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureFlagOverrideStore {

    private final ConcurrentHashMap<String, Object> overrides = new ConcurrentHashMap<>();

    public void set(String key, boolean enabled) {
        overrides.put(key, enabled);
    }

    public void clear(String key) {
        overrides.remove(key);
    }

    public boolean contains(String key) {
        return overrides.containsKey(key);
    }

    public Map<String, Object> asMap() {
        return overrides;
    }
}
