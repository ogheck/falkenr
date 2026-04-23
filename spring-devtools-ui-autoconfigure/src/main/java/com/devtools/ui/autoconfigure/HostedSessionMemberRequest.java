package com.devtools.ui.autoconfigure;

record HostedSessionMemberRequest(
        String memberId,
        String role,
        String source,
        String actor
) {
}
