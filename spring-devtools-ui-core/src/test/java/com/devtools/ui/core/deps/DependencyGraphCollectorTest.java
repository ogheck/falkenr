package com.devtools.ui.core.deps;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphCollectorTest {

    @Test
    void collectsBeanDependenciesAndDependents() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("alphaService", new RootBeanDefinition(AlphaService.class));
        beanFactory.registerBeanDefinition("betaRepository", new RootBeanDefinition(BetaRepository.class));
        beanFactory.registerBeanDefinition("gammaController", new RootBeanDefinition(GammaController.class));
        beanFactory.registerDependentBean("betaRepository", "alphaService");
        beanFactory.registerDependentBean("alphaService", "gammaController");

        DependencyGraphCollector collector = new DependencyGraphCollector(beanFactory);

        assertThat(collector.collect())
                .filteredOn(node -> node.beanName().equals("alphaService"))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.beanType()).isEqualTo("AlphaService");
                    assertThat(node.dependencies()).containsExactly("betaRepository");
                    assertThat(node.dependents()).containsExactly("gammaController");
                });
    }

    static class AlphaService {
    }

    static class BetaRepository {
    }

    static class GammaController {
    }
}
