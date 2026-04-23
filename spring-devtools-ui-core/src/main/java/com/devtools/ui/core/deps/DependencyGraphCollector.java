package com.devtools.ui.core.deps;

import com.devtools.ui.core.collector.DevToolsCollector;
import com.devtools.ui.core.model.DependencyNodeDescriptor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class DependencyGraphCollector implements DevToolsCollector<DependencyNodeDescriptor> {

    private final ConfigurableListableBeanFactory beanFactory;

    public DependencyGraphCollector(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public String id() {
        return "dependencyGraph";
    }

    @Override
    public List<DependencyNodeDescriptor> collect() {
        return Arrays.stream(beanFactory.getBeanDefinitionNames())
                .sorted()
                .map(this::descriptor)
                .sorted(Comparator.comparing(DependencyNodeDescriptor::beanType)
                        .thenComparing(DependencyNodeDescriptor::beanName))
                .toList();
    }

    private DependencyNodeDescriptor descriptor(String beanName) {
        Class<?> beanType = beanFactory.getType(beanName, false);
        BeanDefinition beanDefinition = beanFactory.containsBeanDefinition(beanName)
                ? beanFactory.getBeanDefinition(beanName)
                : null;

        List<String> dependencies = Arrays.stream(beanFactory.getDependenciesForBean(beanName))
                .sorted()
                .toList();
        List<String> dependents = Arrays.stream(beanFactory.getDependentBeans(beanName))
                .sorted()
                .toList();

        return new DependencyNodeDescriptor(
                beanName,
                beanType == null ? "Unknown" : beanType.getSimpleName(),
                beanDefinition == null ? "singleton" : beanDefinition.getScope().isBlank() ? "singleton" : beanDefinition.getScope(),
                dependencies,
                dependents
        );
    }
}
