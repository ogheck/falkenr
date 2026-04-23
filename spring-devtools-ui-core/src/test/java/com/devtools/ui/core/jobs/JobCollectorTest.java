package com.devtools.ui.core.jobs;

import com.devtools.ui.core.collector.DevToolsCollector;
import com.devtools.ui.core.policy.DevToolsDataPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class JobCollectorTest {

    private static final DevToolsDataPolicy DEFAULT_POLICY = new DevToolsDataPolicy() {
        @Override public boolean shouldCaptureRequest(String path) { return true; }
        @Override public boolean captureRequestHeaders() { return true; }
        @Override public boolean captureRequestBodies() { return true; }
        @Override public boolean truncateRequestBodies() { return true; }
        @Override public String sanitizeConfigValue(String key, String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeConfigValue(key, value); }
        @Override public java.util.Map<String, java.util.List<String>> sanitizeHeaders(java.util.Map<String, java.util.List<String>> headers) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeHeaders(headers); }
        @Override public String sanitizeSql(String sql) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeSql(sql); }
        @Override public String sanitizeScheduledValue(String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeScheduledValue(value); }
        @Override public String sanitizePayload(String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizePayload(value); }
        @Override public boolean maskSessionSecrets() { return true; }
        @Override public String sanitizeSessionSecret(String value) { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.sanitizeSessionSecret(value); }
        @Override public String maskValue() { return com.devtools.ui.core.sanitize.SensitiveDataSanitizer.MASKED_VALUE; }
    };

    @Test
    void collectsScheduledMethodsFromBeanFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AnnotationConfigUtils.registerAnnotationConfigProcessors(beanFactory);
        beanFactory.registerBeanDefinition("scheduledBean", new RootBeanDefinition(ScheduledBean.class));

        DevToolsCollector<?> collector = new JobCollector(beanFactory, DEFAULT_POLICY);

        assertThat(collector.id()).isEqualTo("jobs");
        assertThat(collector.collect())
                .singleElement()
                .satisfies(job -> {
                    assertThat(job).extracting("beanName").isEqualTo("scheduledBean");
                    assertThat(job).extracting("beanType").isEqualTo("ScheduledBean");
                    assertThat(job).extracting("methodName").isEqualTo("heartbeat");
                    assertThat(job).extracting("triggerType").isEqualTo("fixedDelay");
                    assertThat(job).extracting("expression").asString().contains("30000");
                });
    }

    @Test
    void masksSensitiveScheduledMetadata() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        AnnotationConfigUtils.registerAnnotationConfigProcessors(beanFactory);
        beanFactory.registerBeanDefinition("sensitiveScheduledBean", new RootBeanDefinition(SensitiveScheduledBean.class));

        DevToolsCollector<?> collector = new JobCollector(beanFactory, DEFAULT_POLICY);

        assertThat(collector.collect())
                .singleElement()
                .satisfies(job -> {
                    assertThat(job).extracting("expression").isEqualTo("${jobs.secret.cron:[masked]}");
                    assertThat(job).extracting("scheduler").isEqualTo("${jobs.scheduler.token:[masked]}");
                });
    }

    @EnableScheduling
    static class ScheduledBean {

        @Scheduled(fixedDelay = 30000L)
        void heartbeat() {
        }
    }

    @EnableScheduling
    static class SensitiveScheduledBean {

        @Scheduled(cron = "${jobs.secret.cron}", scheduler = "${jobs.scheduler.token}")
        void run() {
        }
    }
}
