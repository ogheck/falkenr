package com.devtools.ui.core.model;

import java.util.List;

public record DependencyNodeDescriptor(
        String beanName,
        String beanType,
        String scope,
        List<String> dependencies,
        List<String> dependents
) {
}
