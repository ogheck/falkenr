package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;

import java.util.List;

public record RelaySessionCollaborationRequest(
        String sessionId,
        List<SessionActivityEventDescriptor> activity,
        List<SessionDebugNoteDescriptor> debugNotes,
        List<SessionRecordingDescriptor> recordings
) {
}
