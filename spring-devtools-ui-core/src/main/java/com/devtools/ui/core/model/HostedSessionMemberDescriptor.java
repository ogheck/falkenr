package com.devtools.ui.core.model;

public record HostedSessionMemberDescriptor(
        String memberId,
        String role,
        String source,
        String joinedAt,
        String publishedAt,
        String focusedArtifactType,
        String focusedArtifactId,
        String lastAction
) {
}
