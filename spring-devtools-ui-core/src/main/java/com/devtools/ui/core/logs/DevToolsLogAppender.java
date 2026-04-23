package com.devtools.ui.core.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.devtools.ui.core.model.LogEventDescriptor;

import java.time.Instant;

public class DevToolsLogAppender extends AppenderBase<ILoggingEvent> {

    private final InMemoryLogStore logStore;

    public DevToolsLogAppender(InMemoryLogStore logStore) {
        this.logStore = logStore;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        logStore.append(new LogEventDescriptor(
                Instant.ofEpochMilli(eventObject.getTimeStamp()),
                eventObject.getLevel().levelStr,
                eventObject.getFormattedMessage(),
                eventObject.getLoggerName(),
                eventObject.getThrowableProxy() == null ? null : ThrowableProxyUtil.asString(eventObject.getThrowableProxy())
        ));
    }
}
