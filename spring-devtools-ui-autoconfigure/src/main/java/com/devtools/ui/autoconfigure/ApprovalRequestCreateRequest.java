package com.devtools.ui.autoconfigure;

record ApprovalRequestCreateRequest(
        String permission,
        String target,
        String reason
) {
}
