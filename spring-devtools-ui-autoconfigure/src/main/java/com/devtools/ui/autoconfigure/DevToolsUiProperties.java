package com.devtools.ui.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.devtools.ui")
public class DevToolsUiProperties {

    private boolean enabled = true;
    private final FeatureToggles features = new FeatureToggles();
    private final MemoryBudgets memory = new MemoryBudgets();
    private final HistorySettings history = new HistorySettings();
    private final RemoteSettings remote = new RemoteSettings();
    private final AccessSettings access = new AccessSettings();
    private final RequestSamplingSettings requestSampling = new RequestSamplingSettings();
    private final SecretsSettings secrets = new SecretsSettings();
    private final PolicySettings policy = new PolicySettings();
    private final FeatureFlagSettings featureFlags = new FeatureFlagSettings();
    private int maxCapturedBodyLength = DevToolsUiConstants.DEFAULT_BODY_PREVIEW_LIMIT;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequestLimit() {
        return memory.getRequests();
    }

    public void setRequestLimit(int requestLimit) {
        memory.setRequests(requestLimit);
    }

    public int getLogLimit() {
        return memory.getLogs();
    }

    public void setLogLimit(int logLimit) {
        memory.setLogs(logLimit);
    }

    public int getDbQueryLimit() {
        return memory.getDbQueries();
    }

    public void setDbQueryLimit(int dbQueryLimit) {
        memory.setDbQueries(dbQueryLimit);
    }

    public int getMaxCapturedBodyLength() {
        return maxCapturedBodyLength;
    }

    public void setMaxCapturedBodyLength(int maxCapturedBodyLength) {
        this.maxCapturedBodyLength = maxCapturedBodyLength;
    }

    public FeatureToggles getFeatures() {
        return features;
    }

    public MemoryBudgets getMemory() {
        return memory;
    }

    public HistorySettings getHistory() {
        return history;
    }

    public RemoteSettings getRemote() {
        return remote;
    }

    public AccessSettings getAccess() {
        return access;
    }

    public RequestSamplingSettings getRequestSampling() {
        return requestSampling;
    }

    public SecretsSettings getSecrets() {
        return secrets;
    }

    public PolicySettings getPolicy() {
        return policy;
    }

    public FeatureFlagSettings getFeatureFlags() {
        return featureFlags;
    }

    public static class FeatureToggles {

        private boolean endpoints = true;
        private boolean requests = true;
        private boolean config = true;
        private boolean logs = true;
        private boolean jobs = true;
        private boolean dbQueries = true;
        private boolean featureFlags = true;
        private boolean dependencies = true;
        private boolean fakeServices = true;
        private boolean webhooks = true;
        private boolean time = true;

        public boolean isEndpoints() {
            return endpoints;
        }

        public void setEndpoints(boolean endpoints) {
            this.endpoints = endpoints;
        }

        public boolean isRequests() {
            return requests;
        }

        public void setRequests(boolean requests) {
            this.requests = requests;
        }

        public boolean isConfig() {
            return config;
        }

        public void setConfig(boolean config) {
            this.config = config;
        }

        public boolean isLogs() {
            return logs;
        }

        public void setLogs(boolean logs) {
            this.logs = logs;
        }

        public boolean isJobs() {
            return jobs;
        }

        public void setJobs(boolean jobs) {
            this.jobs = jobs;
        }

        public boolean isDbQueries() {
            return dbQueries;
        }

        public void setDbQueries(boolean dbQueries) {
            this.dbQueries = dbQueries;
        }

        public boolean isFeatureFlags() {
            return featureFlags;
        }

        public void setFeatureFlags(boolean featureFlags) {
            this.featureFlags = featureFlags;
        }

        public boolean isDependencies() {
            return dependencies;
        }

        public void setDependencies(boolean dependencies) {
            this.dependencies = dependencies;
        }

        public boolean isFakeServices() {
            return fakeServices;
        }

        public void setFakeServices(boolean fakeServices) {
            this.fakeServices = fakeServices;
        }

        public boolean isWebhooks() {
            return webhooks;
        }

        public void setWebhooks(boolean webhooks) {
            this.webhooks = webhooks;
        }

        public boolean isTime() {
            return time;
        }

        public void setTime(boolean time) {
            this.time = time;
        }
    }

    public static class MemoryBudgets {

        private int requests = DevToolsUiConstants.DEFAULT_REQUEST_LIMIT;
        private int logs = DevToolsUiConstants.DEFAULT_LOG_LIMIT;
        private int dbQueries = DevToolsUiConstants.DEFAULT_DB_QUERY_LIMIT;
        private int auditLogs = 200;

        public int getRequests() {
            return requests;
        }

        public void setRequests(int requests) {
            this.requests = Math.max(0, requests);
        }

        public int getLogs() {
            return logs;
        }

        public void setLogs(int logs) {
            this.logs = Math.max(0, logs);
        }

        public int getDbQueries() {
            return dbQueries;
        }

        public void setDbQueries(int dbQueries) {
            this.dbQueries = Math.max(0, dbQueries);
        }

        public int getAuditLogs() {
            return auditLogs;
        }

        public void setAuditLogs(int auditLogs) {
            this.auditLogs = Math.max(0, auditLogs);
        }
    }

    public static class HistorySettings {

        private boolean persistRequests;
        private String requestsPersistenceFile = ".spring-devtools-ui/request-history.json";
        private int maxPersistedRequests = 500;
        private boolean persistConfigSnapshots = true;
        private String configSnapshotsFile = ".spring-devtools-ui/config-snapshots.json";
        private int maxConfigSnapshots = 20;

        public boolean isPersistRequests() {
            return persistRequests;
        }

        public void setPersistRequests(boolean persistRequests) {
            this.persistRequests = persistRequests;
        }

        public String getRequestsPersistenceFile() {
            return requestsPersistenceFile;
        }

        public void setRequestsPersistenceFile(String requestsPersistenceFile) {
            this.requestsPersistenceFile = requestsPersistenceFile == null || requestsPersistenceFile.isBlank()
                    ? ".spring-devtools-ui/request-history.json"
                    : requestsPersistenceFile.trim();
        }

        public int getMaxPersistedRequests() {
            return maxPersistedRequests;
        }

        public void setMaxPersistedRequests(int maxPersistedRequests) {
            this.maxPersistedRequests = Math.max(1, maxPersistedRequests);
        }

        public boolean isPersistConfigSnapshots() {
            return persistConfigSnapshots;
        }

        public void setPersistConfigSnapshots(boolean persistConfigSnapshots) {
            this.persistConfigSnapshots = persistConfigSnapshots;
        }

        public String getConfigSnapshotsFile() {
            return configSnapshotsFile;
        }

        public void setConfigSnapshotsFile(String configSnapshotsFile) {
            this.configSnapshotsFile = configSnapshotsFile == null || configSnapshotsFile.isBlank()
                    ? ".spring-devtools-ui/config-snapshots.json"
                    : configSnapshotsFile.trim();
        }

        public int getMaxConfigSnapshots() {
            return maxConfigSnapshots;
        }

        public void setMaxConfigSnapshots(int maxConfigSnapshots) {
            this.maxConfigSnapshots = Math.max(1, maxConfigSnapshots);
        }
    }

    public static class FeatureFlagSettings {

        private boolean persistDefinitions = true;
        private String definitionsFile = ".spring-devtools-ui/feature-flag-definitions.json";
        private boolean requireAdminRoleForMutations = true;

        public boolean isPersistDefinitions() {
            return persistDefinitions;
        }

        public void setPersistDefinitions(boolean persistDefinitions) {
            this.persistDefinitions = persistDefinitions;
        }

        public String getDefinitionsFile() {
            return definitionsFile;
        }

        public void setDefinitionsFile(String definitionsFile) {
            this.definitionsFile = definitionsFile == null || definitionsFile.isBlank()
                    ? ".spring-devtools-ui/feature-flag-definitions.json"
                    : definitionsFile.trim();
        }

        public boolean isRequireAdminRoleForMutations() {
            return requireAdminRoleForMutations;
        }

        public void setRequireAdminRoleForMutations(boolean requireAdminRoleForMutations) {
            this.requireAdminRoleForMutations = requireAdminRoleForMutations;
        }
    }

    public static class RemoteSettings {

        private String transportMode = "local-stub";
        private String relayBaseUrl = "wss://relay.spring-devtools-ui.dev";
        private String relayApiBaseUrl = "https://relay.spring-devtools-ui.dev/api";
        private String appBaseUrl = "https://app.spring-devtools-ui.dev";
        private int tokenTtlMinutes = 480;
        private int heartbeatIntervalSeconds = 30;
        private int reconnectDelaySeconds = 10;
        private int sessionActivityLimit = 25;
        private int sessionReplayLimit = 50;
        private int sessionRecordingLimit = 10;
        private int sessionAuditLimit = 50;
        private boolean simulateAttachFailure;
        private boolean simulateHeartbeatFailure;

        public String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode == null || transportMode.isBlank() ? "local-stub" : transportMode.trim();
        }

        public String getRelayBaseUrl() {
            return relayBaseUrl;
        }

        public void setRelayBaseUrl(String relayBaseUrl) {
            this.relayBaseUrl = relayBaseUrl;
        }

        public String getRelayApiBaseUrl() {
            return relayApiBaseUrl;
        }

        public void setRelayApiBaseUrl(String relayApiBaseUrl) {
            this.relayApiBaseUrl = relayApiBaseUrl;
        }

        public String getAppBaseUrl() {
            return appBaseUrl;
        }

        public void setAppBaseUrl(String appBaseUrl) {
            this.appBaseUrl = appBaseUrl;
        }

        public int getTokenTtlMinutes() {
            return tokenTtlMinutes;
        }

        public void setTokenTtlMinutes(int tokenTtlMinutes) {
            this.tokenTtlMinutes = Math.max(1, tokenTtlMinutes);
        }

        public int getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = Math.max(5, heartbeatIntervalSeconds);
        }

        public int getReconnectDelaySeconds() {
            return reconnectDelaySeconds;
        }

        public void setReconnectDelaySeconds(int reconnectDelaySeconds) {
            this.reconnectDelaySeconds = Math.max(1, reconnectDelaySeconds);
        }

        public int getSessionActivityLimit() {
            return sessionActivityLimit;
        }

        public void setSessionActivityLimit(int sessionActivityLimit) {
            this.sessionActivityLimit = Math.max(0, sessionActivityLimit);
        }

        public int getSessionReplayLimit() {
            return sessionReplayLimit;
        }

        public void setSessionReplayLimit(int sessionReplayLimit) {
            this.sessionReplayLimit = Math.max(0, sessionReplayLimit);
        }

        public int getSessionRecordingLimit() {
            return sessionRecordingLimit;
        }

        public void setSessionRecordingLimit(int sessionRecordingLimit) {
            this.sessionRecordingLimit = Math.max(0, sessionRecordingLimit);
        }

        public int getSessionAuditLimit() {
            return sessionAuditLimit;
        }

        public void setSessionAuditLimit(int sessionAuditLimit) {
            this.sessionAuditLimit = Math.max(0, sessionAuditLimit);
        }

        public boolean isSimulateAttachFailure() {
            return simulateAttachFailure;
        }

        public void setSimulateAttachFailure(boolean simulateAttachFailure) {
            this.simulateAttachFailure = simulateAttachFailure;
        }

        public boolean isSimulateHeartbeatFailure() {
            return simulateHeartbeatFailure;
        }

        public void setSimulateHeartbeatFailure(boolean simulateHeartbeatFailure) {
            this.simulateHeartbeatFailure = simulateHeartbeatFailure;
        }
    }

    public static class AccessSettings {

        private boolean enabled = true;
        private String mode = "localhost";
        private String authToken;
        private java.util.List<String> allowedRoles = java.util.List.of("viewer");
        private String authHeader = "X-DevTools-Auth";
        private String roleHeader = "X-DevTools-Role";
        private String emergencyDisableFile;
        private final SsoSettings sso = new SsoSettings();
        private final RbacSettings rbac = new RbacSettings();
        private final ApprovalSettings approval = new ApprovalSettings();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode == null || mode.isBlank() ? "localhost" : mode.trim();
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public java.util.List<String> getAllowedRoles() {
            return allowedRoles;
        }

        public void setAllowedRoles(java.util.List<String> allowedRoles) {
            this.allowedRoles = allowedRoles == null || allowedRoles.isEmpty() ? java.util.List.of("viewer") : java.util.List.copyOf(allowedRoles);
        }

        public String getAuthHeader() {
            return authHeader;
        }

        public void setAuthHeader(String authHeader) {
            this.authHeader = authHeader == null || authHeader.isBlank() ? "X-DevTools-Auth" : authHeader.trim();
        }

        public String getRoleHeader() {
            return roleHeader;
        }

        public void setRoleHeader(String roleHeader) {
            this.roleHeader = roleHeader == null || roleHeader.isBlank() ? "X-DevTools-Role" : roleHeader.trim();
        }

        public String getEmergencyDisableFile() {
            return emergencyDisableFile;
        }

        public void setEmergencyDisableFile(String emergencyDisableFile) {
            this.emergencyDisableFile = emergencyDisableFile == null || emergencyDisableFile.isBlank()
                    ? null
                    : emergencyDisableFile.trim();
        }

        public SsoSettings getSso() {
            return sso;
        }

        public RbacSettings getRbac() {
            return rbac;
        }

        public ApprovalSettings getApproval() {
            return approval;
        }
    }

    public static class SsoSettings {

        private String subjectHeader = "X-Forwarded-User";
        private String emailHeader = "X-Forwarded-Email";
        private String groupsHeader = "X-Forwarded-Groups";
        private java.util.List<String> allowedDomains = java.util.List.of();
        private java.util.List<String> adminGroups = java.util.List.of("devtools-admin");
        private java.util.List<String> viewerGroups = java.util.List.of("devtools-viewer");

        public String getSubjectHeader() {
            return subjectHeader;
        }

        public void setSubjectHeader(String subjectHeader) {
            this.subjectHeader = subjectHeader == null || subjectHeader.isBlank() ? "X-Forwarded-User" : subjectHeader.trim();
        }

        public String getEmailHeader() {
            return emailHeader;
        }

        public void setEmailHeader(String emailHeader) {
            this.emailHeader = emailHeader == null || emailHeader.isBlank() ? "X-Forwarded-Email" : emailHeader.trim();
        }

        public String getGroupsHeader() {
            return groupsHeader;
        }

        public void setGroupsHeader(String groupsHeader) {
            this.groupsHeader = groupsHeader == null || groupsHeader.isBlank() ? "X-Forwarded-Groups" : groupsHeader.trim();
        }

        public java.util.List<String> getAllowedDomains() {
            return allowedDomains;
        }

        public void setAllowedDomains(java.util.List<String> allowedDomains) {
            this.allowedDomains = allowedDomains == null ? java.util.List.of() : java.util.List.copyOf(allowedDomains);
        }

        public java.util.List<String> getAdminGroups() {
            return adminGroups;
        }

        public void setAdminGroups(java.util.List<String> adminGroups) {
            this.adminGroups = adminGroups == null ? java.util.List.of() : java.util.List.copyOf(adminGroups);
        }

        public java.util.List<String> getViewerGroups() {
            return viewerGroups;
        }

        public void setViewerGroups(java.util.List<String> viewerGroups) {
            this.viewerGroups = viewerGroups == null ? java.util.List.of() : java.util.List.copyOf(viewerGroups);
        }
    }

    public static class RbacSettings {

        private java.util.List<String> viewerPermissions = java.util.List.of();
        private java.util.List<String> adminPermissions = java.util.List.of(
                "config.write",
                "feature-flags.write",
                "fake-services.write",
                "time.write",
                "session.control",
                "webhooks.send",
                "api-testing.send",
                "approvals.review"
        );

        public java.util.List<String> getViewerPermissions() {
            return viewerPermissions;
        }

        public void setViewerPermissions(java.util.List<String> viewerPermissions) {
            this.viewerPermissions = viewerPermissions == null ? java.util.List.of() : java.util.List.copyOf(viewerPermissions);
        }

        public java.util.List<String> getAdminPermissions() {
            return adminPermissions;
        }

        public void setAdminPermissions(java.util.List<String> adminPermissions) {
            this.adminPermissions = adminPermissions == null ? java.util.List.of() : java.util.List.copyOf(adminPermissions);
        }
    }

    public static class ApprovalSettings {

        private boolean enabled;
        private int ttlMinutes = 30;
        private java.util.List<String> requiredPermissions = java.util.List.of(
                "time.write",
                "webhooks.send",
                "api-testing.send"
        );

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = Math.max(1, ttlMinutes);
        }

        public java.util.List<String> getRequiredPermissions() {
            return requiredPermissions;
        }

        public void setRequiredPermissions(java.util.List<String> requiredPermissions) {
            this.requiredPermissions = requiredPermissions == null ? java.util.List.of() : java.util.List.copyOf(requiredPermissions);
        }
    }

    public static class RequestSamplingSettings {

        private boolean enabled;
        private int percentage = 10;
        private boolean alwaysCaptureErrors = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPercentage() {
            return percentage;
        }

        public void setPercentage(int percentage) {
            this.percentage = Math.max(0, Math.min(100, percentage));
        }

        public boolean isAlwaysCaptureErrors() {
            return alwaysCaptureErrors;
        }

        public void setAlwaysCaptureErrors(boolean alwaysCaptureErrors) {
            this.alwaysCaptureErrors = alwaysCaptureErrors;
        }
    }

    public static class SecretsSettings {

        private boolean maskSessionSecrets;

        public boolean isMaskSessionSecrets() {
            return maskSessionSecrets;
        }

        public void setMaskSessionSecrets(boolean maskSessionSecrets) {
            this.maskSessionSecrets = maskSessionSecrets;
        }
    }

    public static class PolicySettings {

        private boolean captureRequestHeaders = true;
        private boolean captureRequestBodies = true;
        private boolean truncateRequestBodies = true;
        private boolean maskRequestHeaders = true;
        private boolean maskConfigValues = true;
        private boolean maskSql = true;
        private boolean maskPayloads = true;
        private boolean maskScheduledValues = true;
        private boolean maskSessionSecrets = true;
        private java.util.List<String> excludedPaths = java.util.List.of();

        public boolean isCaptureRequestHeaders() {
            return captureRequestHeaders;
        }

        public void setCaptureRequestHeaders(boolean captureRequestHeaders) {
            this.captureRequestHeaders = captureRequestHeaders;
        }

        public boolean isCaptureRequestBodies() {
            return captureRequestBodies;
        }

        public void setCaptureRequestBodies(boolean captureRequestBodies) {
            this.captureRequestBodies = captureRequestBodies;
        }

        public boolean isTruncateRequestBodies() {
            return truncateRequestBodies;
        }

        public void setTruncateRequestBodies(boolean truncateRequestBodies) {
            this.truncateRequestBodies = truncateRequestBodies;
        }

        public boolean isMaskRequestHeaders() {
            return maskRequestHeaders;
        }

        public void setMaskRequestHeaders(boolean maskRequestHeaders) {
            this.maskRequestHeaders = maskRequestHeaders;
        }

        public boolean isMaskConfigValues() {
            return maskConfigValues;
        }

        public void setMaskConfigValues(boolean maskConfigValues) {
            this.maskConfigValues = maskConfigValues;
        }

        public boolean isMaskSql() {
            return maskSql;
        }

        public void setMaskSql(boolean maskSql) {
            this.maskSql = maskSql;
        }

        public boolean isMaskPayloads() {
            return maskPayloads;
        }

        public void setMaskPayloads(boolean maskPayloads) {
            this.maskPayloads = maskPayloads;
        }

        public boolean isMaskScheduledValues() {
            return maskScheduledValues;
        }

        public void setMaskScheduledValues(boolean maskScheduledValues) {
            this.maskScheduledValues = maskScheduledValues;
        }

        public boolean isMaskSessionSecrets() {
            return maskSessionSecrets;
        }

        public void setMaskSessionSecrets(boolean maskSessionSecrets) {
            this.maskSessionSecrets = maskSessionSecrets;
        }

        public java.util.List<String> getExcludedPaths() {
            return excludedPaths;
        }

        public void setExcludedPaths(java.util.List<String> excludedPaths) {
            this.excludedPaths = excludedPaths == null ? java.util.List.of() : java.util.List.copyOf(excludedPaths);
        }
    }
}
