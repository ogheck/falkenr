package com.devtools.ui.core.model;

import java.util.List;

public record HostedSessionViewDescriptor(
        String sessionId,
        String viewId,
        String syncId,
        boolean available,
        String durableState,
        long sessionVersion,
        Long publishedVersion,
        String relayViewerUrl,
        String ownerName,
        String accessScope,
        int activeMemberCount,
        int replayCount,
        int debugNoteCount,
        int recordingCount,
        String focusedArtifactType,
        String focusedArtifactId,
        String lastPublishedAt,
        List<HostedSessionMemberDescriptor> members,
        List<String> recentActors,
        List<String> recentReplayTitles
) {
}
