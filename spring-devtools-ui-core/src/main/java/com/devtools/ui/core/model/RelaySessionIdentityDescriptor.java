package com.devtools.ui.core.model;

import java.util.List;

public record RelaySessionIdentityDescriptor(
        String sessionId,
        RelayOrganizationDescriptor organization,
        RelayAccountDescriptor owner,
        List<RelayAccountDescriptor> collaborators
) {
}
