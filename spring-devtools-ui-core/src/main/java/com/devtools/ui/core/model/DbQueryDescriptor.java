package com.devtools.ui.core.model;

import java.time.Instant;

public record DbQueryDescriptor(
        Instant timestamp,
        String sql,
        String statementType,
        String dataSource,
        Integer rowsAffected
) {
}
