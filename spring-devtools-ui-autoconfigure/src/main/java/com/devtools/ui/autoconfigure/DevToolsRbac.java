package com.devtools.ui.autoconfigure;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.Set;

final class DevToolsRbac {

    private DevToolsRbac() {
    }

    static Set<String> permissions(DevToolsUiProperties.AccessSettings settings) {
        if (isLocalMode(settings)) {
            return new LinkedHashSet<>(settings.getRbac().getAdminPermissions());
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String role = attributes == null
                ? "viewer"
                : stringAttribute(attributes, DevToolsAccessAttributes.ROLE, "viewer");
        return permissionsForRole(role, settings);
    }

    static void require(DevToolsUiProperties.AccessSettings settings, DevToolsPermission permission) {
        if (permissions(settings).contains(permission.value())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + permission.value());
    }

    static Set<String> permissionsForRole(String role, DevToolsUiProperties.AccessSettings settings) {
        if ("admin".equalsIgnoreCase(role)) {
            return new LinkedHashSet<>(settings.getRbac().getAdminPermissions());
        }
        return new LinkedHashSet<>(settings.getRbac().getViewerPermissions());
    }

    static String actor() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? "local-operator" : stringAttribute(attributes, DevToolsAccessAttributes.ACTOR, "local-operator");
    }

    static String role() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? "viewer" : stringAttribute(attributes, DevToolsAccessAttributes.ROLE, "viewer");
    }

    private static boolean isLocalMode(DevToolsUiProperties.AccessSettings settings) {
        return settings.getMode() == null
                || settings.getMode().isBlank()
                || "localhost".equalsIgnoreCase(settings.getMode());
    }

    private static String stringAttribute(ServletRequestAttributes attributes, String name, String defaultValue) {
        Object value = attributes.getRequest().getAttribute(name);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : defaultValue;
    }
}
