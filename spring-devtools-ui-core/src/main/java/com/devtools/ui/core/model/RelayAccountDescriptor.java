package com.devtools.ui.core.model;

public record RelayAccountDescriptor(
        String accountId,
        String displayName,
        String organizationId,
        String role
) {
}
