package com.devtools.ui.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

class DevToolsAccessFilter extends OncePerRequestFilter {

    private final DevToolsUiProperties.AccessSettings accessSettings;

    DevToolsAccessFilter(DevToolsUiProperties.AccessSettings accessSettings) {
        this.accessSettings = accessSettings;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(DevToolsUiConstants.ROOT_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isDisabled()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, disabledMessage());
            return;
        }

        if (isAllowed(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, denialMessage());
    }

    private boolean isAllowed(HttpServletRequest request) {
        clearIdentity(request);
        if ("sso".equalsIgnoreCase(accessSettings.getMode())) {
            return hasValidSsoIdentity(request);
        }
        if ("staging".equalsIgnoreCase(accessSettings.getMode())) {
            return hasValidStagingCredentials(request);
        }
        return isLoopbackRequest(request);
    }

    private boolean hasValidStagingCredentials(HttpServletRequest request) {
        if (!StringUtils.hasText(accessSettings.getAuthToken())) {
            return false;
        }
        String providedToken = request.getHeader(accessSettings.getAuthHeader());
        if (!accessSettings.getAuthToken().equals(providedToken)) {
            return false;
        }
        String providedRole = request.getHeader(accessSettings.getRoleHeader());
        List<String> allowedRoles = accessSettings.getAllowedRoles();
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            request.setAttribute(DevToolsAccessAttributes.ACTOR, providedRole == null || providedRole.isBlank() ? "staging-user" : providedRole.trim());
            request.setAttribute(DevToolsAccessAttributes.ROLE, providedRole == null || providedRole.isBlank() ? "viewer" : providedRole.trim());
            return true;
        }
        boolean allowed = StringUtils.hasText(providedRole) && allowedRoles.stream().anyMatch(role -> role.equalsIgnoreCase(providedRole.trim()));
        if (allowed) {
            request.setAttribute(DevToolsAccessAttributes.ACTOR, providedRole.trim());
            request.setAttribute(DevToolsAccessAttributes.ROLE, providedRole.trim());
        }
        return allowed;
    }

    private boolean hasValidSsoIdentity(HttpServletRequest request) {
        DevToolsUiProperties.SsoSettings sso = accessSettings.getSso();
        String subject = normalize(request.getHeader(sso.getSubjectHeader()));
        String email = normalize(request.getHeader(sso.getEmailHeader()));
        if (!StringUtils.hasText(subject) && !StringUtils.hasText(email)) {
            return false;
        }
        if (StringUtils.hasText(email) && !isAllowedDomain(email, sso.getAllowedDomains())) {
            return false;
        }
        List<String> groups = parseGroups(request.getHeader(sso.getGroupsHeader()));
        String derivedRole = deriveRole(groups, sso);
        if (!isAllowedRole(derivedRole)) {
            return false;
        }
        request.setAttribute(DevToolsAccessAttributes.ACTOR, StringUtils.hasText(email) ? email : subject);
        request.setAttribute(DevToolsAccessAttributes.EMAIL, email);
        request.setAttribute(DevToolsAccessAttributes.ROLE, derivedRole);
        request.setAttribute(DevToolsAccessAttributes.GROUPS, groups);
        return true;
    }

    private boolean isAllowedRole(String role) {
        List<String> allowedRoles = accessSettings.getAllowedRoles();
        return allowedRoles == null || allowedRoles.isEmpty() || allowedRoles.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(role));
    }

    private String deriveRole(List<String> groups, DevToolsUiProperties.SsoSettings sso) {
        if (matchesAny(groups, sso.getAdminGroups())) {
            return "admin";
        }
        if (matchesAny(groups, sso.getViewerGroups())) {
            return "viewer";
        }
        return "viewer";
    }

    private boolean matchesAny(List<String> groups, List<String> configuredGroups) {
        if (configuredGroups == null || configuredGroups.isEmpty()) {
            return false;
        }
        return groups.stream().anyMatch(group -> configuredGroups.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(group)));
    }

    private boolean isAllowedDomain(String email, List<String> allowedDomains) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            return true;
        }
        int atIndex = email.indexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return false;
        }
        String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        return allowedDomains.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(domain::equals);
    }

    private List<String> parseGroups(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return List.of();
        }
        return Arrays.stream(headerValue.split(","))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private void clearIdentity(HttpServletRequest request) {
        request.removeAttribute(DevToolsAccessAttributes.ACTOR);
        request.removeAttribute(DevToolsAccessAttributes.ROLE);
        request.removeAttribute(DevToolsAccessAttributes.EMAIL);
        request.removeAttribute(DevToolsAccessAttributes.GROUPS);
    }

    private String denialMessage() {
        return "staging".equalsIgnoreCase(accessSettings.getMode())
                ? "spring-devtools-ui staging access requires valid auth headers"
                : "sso".equalsIgnoreCase(accessSettings.getMode())
                ? "spring-devtools-ui sso access requires trusted identity headers"
                : "spring-devtools-ui is only available from localhost";
    }

    private boolean isDisabled() {
        if (!accessSettings.isEnabled()) {
            return true;
        }
        String emergencyDisableFile = accessSettings.getEmergencyDisableFile();
        return StringUtils.hasText(emergencyDisableFile) && Files.exists(Path.of(emergencyDisableFile));
    }

    private String disabledMessage() {
        if (!accessSettings.isEnabled()) {
            return "spring-devtools-ui access has been disabled for this environment";
        }
        return "spring-devtools-ui emergency disable file is active";
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String forwardedClient = forwardedFor.split(",")[0].trim();
            return isLoopbackAddress(forwardedClient);
        }

        return isLoopbackAddress(request.getRemoteAddr());
    }

    private boolean isLoopbackAddress(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
