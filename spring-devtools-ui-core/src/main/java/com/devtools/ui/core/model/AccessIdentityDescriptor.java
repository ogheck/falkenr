package com.devtools.ui.core.model;

import java.util.List;

public record AccessIdentityDescriptor(
        String mode,
        boolean authenticated,
        String actor,
        String role,
        String email,
        List<String> groups,
        List<String> permissions
) {
}
