package com.devtools.ui.core.model;

public record ApprovalRequestDescriptor(
        String approvalId,
        String permission,
        String target,
        String reason,
        String requestedBy,
        String status,
        String createdAt,
        String expiresAt,
        String approvedBy,
        String approvedAt,
        String consumedBy,
        String consumedAt
) {
}
