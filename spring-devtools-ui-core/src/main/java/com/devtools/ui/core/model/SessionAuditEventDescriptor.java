package com.devtools.ui.core.model;

public record SessionAuditEventDescriptor(
        String auditId,
        String eventType,
        String actor,
        String detail,
        String recordedAt,
        String organizationId,
        String sessionId
) {
}
