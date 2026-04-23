package com.devtools.ui.core.model;

import java.util.List;

public record SessionRecordingDescriptor(
        String recordingId,
        String startedBy,
        String startedAt,
        String stoppedAt,
        boolean active,
        int activityCount,
        int replayCount,
        int debugNoteCount,
        int activeMemberCount,
        String focusedArtifactType,
        String focusedArtifactId,
        List<String> highlights
) {
}
