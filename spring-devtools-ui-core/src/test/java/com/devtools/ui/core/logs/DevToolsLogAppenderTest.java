package com.devtools.ui.core.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DevToolsLogAppenderTest {

    @Test
    void appendWritesStructuredLogEventToStore() {
        InMemoryLogStore store = new InMemoryLogStore(10);
        DevToolsLogAppender appender = new DevToolsLogAppender(store);
        appender.start();

        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.WARN);
        event.setLoggerName("com.devtools.test.Logger");
        event.setMessage("Background task failed");
        event.setTimeStamp(Instant.parse("2026-04-07T20:00:00Z").toEpochMilli());

        appender.doAppend(event);

        assertThat(store.snapshot()).singleElement().satisfies(logEvent -> {
            assertThat(logEvent.timestamp()).isEqualTo(Instant.parse("2026-04-07T20:00:00Z"));
            assertThat(logEvent.level()).isEqualTo("WARN");
            assertThat(logEvent.message()).isEqualTo("Background task failed");
            assertThat(logEvent.logger()).isEqualTo("com.devtools.test.Logger");
            assertThat(logEvent.stackTrace()).isNull();
        });
    }

    @Test
    void appendCapturesThrowableStackTraceWhenPresent() {
        InMemoryLogStore store = new InMemoryLogStore(10);
        DevToolsLogAppender appender = new DevToolsLogAppender(store);
        appender.start();

        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.ERROR);
        event.setLoggerName("com.devtools.test.Logger");
        event.setMessage("Request failed");
        event.setTimeStamp(Instant.parse("2026-04-07T20:01:00Z").toEpochMilli());
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("Boom")));

        appender.doAppend(event);

        assertThat(store.snapshot()).singleElement().satisfies(logEvent ->
                assertThat(logEvent.stackTrace()).contains("IllegalStateException: Boom"));
    }
}
