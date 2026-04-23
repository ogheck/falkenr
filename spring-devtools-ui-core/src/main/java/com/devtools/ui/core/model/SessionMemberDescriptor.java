package com.devtools.ui.core.model;

public record SessionMemberDescriptor(
        String memberId,
        String role,
        String source,
        String joinedAt,
        String lastSeenAt
) {
}
