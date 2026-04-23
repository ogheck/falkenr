package com.devtools.ui.core.flags;

import com.devtools.ui.core.model.FeatureFlagDefinitionDescriptor;

import java.util.List;

public interface FeatureFlagDefinitionLookup {

    List<FeatureFlagDefinitionDescriptor> definitions();

    FeatureFlagDefinitionDescriptor find(String key);
}
