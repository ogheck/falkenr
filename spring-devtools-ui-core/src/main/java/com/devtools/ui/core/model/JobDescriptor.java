package com.devtools.ui.core.model;

public record JobDescriptor(
        String beanName,
        String beanType,
        String methodName,
        String triggerType,
        String expression,
        String scheduler
) {
}
