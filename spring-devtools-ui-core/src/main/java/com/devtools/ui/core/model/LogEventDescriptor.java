package com.devtools.ui.core.model;

import java.time.Instant;

public record LogEventDescriptor(
        Instant timestamp,
        String level,
        String message,
        String logger,
        String stackTrace
) {
}
