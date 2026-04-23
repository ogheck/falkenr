package com.devtools.ui.autoconfigure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.devtools.ui.core.logs.DevToolsLogAppender;
import com.devtools.ui.core.logs.InMemoryLogStore;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

class DevToolsLogbackInitializer implements InitializingBean, DisposableBean {

    private static final String APPENDER_NAME = "SPRING_DEVTOOLS_UI";

    private final boolean enabled;
    private final InMemoryLogStore logStore;

    DevToolsLogbackInitializer(boolean enabled, InMemoryLogStore logStore) {
        this.enabled = enabled;
        this.logStore = logStore;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> existingAppender = rootLogger.getAppender(APPENDER_NAME);
        if (existingAppender != null) {
            rootLogger.detachAppender(existingAppender);
            existingAppender.stop();
        }

        DevToolsLogAppender appender = new DevToolsLogAppender(logStore);
        appender.setContext(context);
        appender.setName(APPENDER_NAME);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @Override
    public void destroy() {
        if (!enabled) {
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> appender = rootLogger.getAppender(APPENDER_NAME);
        if (appender != null) {
            rootLogger.detachAppender(appender);
            appender.stop();
        }
    }
}
