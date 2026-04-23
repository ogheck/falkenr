package com.devtools.ui.core.jobs;

import com.devtools.ui.core.collector.DevToolsCollector;
import com.devtools.ui.core.model.JobDescriptor;
import com.devtools.ui.core.policy.DevToolsDataPolicy;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JobCollector implements DevToolsCollector<JobDescriptor> {

    private final ListableBeanFactory beanFactory;
    private final DevToolsDataPolicy dataPolicy;

    public JobCollector(ListableBeanFactory beanFactory, DevToolsDataPolicy dataPolicy) {
        this.beanFactory = beanFactory;
        this.dataPolicy = dataPolicy;
    }

    @Override
    public String id() {
        return "jobs";
    }

    @Override
    public List<JobDescriptor> collect() {
        List<JobDescriptor> jobs = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null) {
                continue;
            }

            Class<?> targetType = AopUtils.getTargetClass(beanFactory.getBean(beanName));
            MethodIntrospector.selectMethods(targetType, (Method method) -> {
                List<Scheduled> schedules = scheduledAnnotations(method);
                if (schedules.isEmpty()) {
                    return null;
                }
                for (Scheduled schedule : schedules) {
                    jobs.add(descriptor(beanName, targetType, method, schedule));
                }
                return method;
            });
        }

        return jobs.stream()
                .sorted(Comparator.comparing(JobDescriptor::beanType)
                        .thenComparing(JobDescriptor::methodName)
                        .thenComparing(JobDescriptor::triggerType)
                        .thenComparing(JobDescriptor::expression))
                .toList();
    }

    private List<Scheduled> scheduledAnnotations(Method method) {
        List<Scheduled> directSchedules = AnnotatedElementUtils.findMergedRepeatableAnnotations(method, Scheduled.class, Schedules.class)
                .stream()
                .toList();
        return directSchedules;
    }

    private JobDescriptor descriptor(String beanName, Class<?> beanType, Method method, Scheduled schedule) {
        return new JobDescriptor(
                beanName,
                beanType.getSimpleName(),
                method.getName(),
                triggerType(schedule),
                dataPolicy.sanitizeScheduledValue(expression(schedule)),
                dataPolicy.sanitizeScheduledValue(schedule.scheduler())
        );
    }

    private String triggerType(Scheduled schedule) {
        if (StringUtils.hasText(schedule.cron())) {
            return "cron";
        }
        if (StringUtils.hasText(schedule.fixedDelayString()) || schedule.fixedDelay() >= 0) {
            return "fixedDelay";
        }
        if (StringUtils.hasText(schedule.fixedRateString()) || schedule.fixedRate() >= 0) {
            return "fixedRate";
        }
        if (StringUtils.hasText(schedule.initialDelayString()) || schedule.initialDelay() > 0) {
            return "initialDelay";
        }
        return "scheduled";
    }

    private String expression(Scheduled schedule) {
        if (StringUtils.hasText(schedule.cron())) {
            return schedule.cron();
        }
        if (StringUtils.hasText(schedule.fixedDelayString())) {
            return schedule.fixedDelayString();
        }
        if (schedule.fixedDelay() >= 0) {
            return schedule.fixedDelay() + "ms";
        }
        if (StringUtils.hasText(schedule.fixedRateString())) {
            return schedule.fixedRateString();
        }
        if (schedule.fixedRate() >= 0) {
            return schedule.fixedRate() + "ms";
        }
        if (StringUtils.hasText(schedule.initialDelayString())) {
            return schedule.initialDelayString();
        }
        if (schedule.initialDelay() > 0) {
            return schedule.initialDelay() + "ms";
        }
        return "";
    }
}
