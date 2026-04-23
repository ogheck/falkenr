package com.devtools.ui.core.model;

public record SessionWorkspaceMemberDescriptor(
        String memberId,
        String role,
        String source,
        String joinedAt,
        String lastSeenAt,
        String focusedArtifactType,
        String focusedArtifactId,
        String lastAction
) {
}
