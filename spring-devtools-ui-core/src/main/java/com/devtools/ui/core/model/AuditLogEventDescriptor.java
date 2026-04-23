package com.devtools.ui.core.model;

public record AuditLogEventDescriptor(
        String auditId,
        String category,
        String action,
        String actor,
        String detail,
        String timestamp
) {
}
