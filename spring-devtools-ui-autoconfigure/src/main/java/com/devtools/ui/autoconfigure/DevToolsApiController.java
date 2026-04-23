package com.devtools.ui.autoconfigure;

import com.devtools.ui.autoconfigure.api.PagedResponse;
import com.devtools.ui.autoconfigure.api.PagedResponseFactory;
import com.devtools.ui.core.audit.InMemoryAuditLogStore;
import com.devtools.ui.core.model.AuditLogEventDescriptor;
import com.devtools.ui.core.model.AccessIdentityDescriptor;
import com.devtools.ui.core.model.ApprovalRequestDescriptor;
import com.devtools.ui.core.config.ConfigInspector;
import com.devtools.ui.core.db.DbQueryCollector;
import com.devtools.ui.core.deps.DependencyGraphCollector;
import com.devtools.ui.core.endpoint.EndpointCollector;
import com.devtools.ui.core.fakes.FakeExternalServiceCollector;
import com.devtools.ui.core.fakes.FakeExternalServiceStore;
import com.devtools.ui.core.flags.FeatureFlagCollector;
import com.devtools.ui.core.flags.FeatureFlagOverrideStore;
import com.devtools.ui.core.jobs.JobCollector;
import com.devtools.ui.core.logs.InMemoryLogStore;
import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.model.ConfigComparisonDescriptor;
import com.devtools.ui.core.model.ConfigDriftDescriptor;
import com.devtools.ui.core.model.ConfigPropertyDescriptor;
import com.devtools.ui.core.model.ConfigSnapshotDescriptor;
import com.devtools.ui.core.model.DbQueryDescriptor;
import com.devtools.ui.core.model.DependencyNodeDescriptor;
import com.devtools.ui.core.model.EndpointDescriptor;
import com.devtools.ui.core.model.FakeExternalServiceDescriptor;
import com.devtools.ui.core.model.FeatureFlagDescriptor;
import com.devtools.ui.core.model.FeatureFlagDefinitionDescriptor;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import com.devtools.ui.core.model.JobDescriptor;
import com.devtools.ui.core.model.LogEventDescriptor;
import com.devtools.ui.core.model.RemoteSessionDescriptor;
import com.devtools.ui.core.model.RelaySessionIdentityDescriptor;
import com.devtools.ui.core.model.RelayViewerSessionDescriptor;
import com.devtools.ui.core.model.SessionAccessValidationDescriptor;
import com.devtools.ui.core.model.SessionReplayEntryDescriptor;
import com.devtools.ui.core.model.SessionShareTokenDescriptor;
import com.devtools.ui.core.model.TimeTravelStateDescriptor;
import com.devtools.ui.core.model.WebhookTargetDescriptor;
import com.devtools.ui.core.requests.RequestCaptureStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
class DevToolsApiController {

    private final EndpointCollector endpointCollector;
    private final RequestCaptureStore requestCaptureStore;
    private final ConfigInspector configInspector;
    private final JsonFileConfigSnapshotStore configSnapshotStore;
    private final JsonFileFeatureFlagDefinitionStore featureFlagDefinitionStore;
    private final InMemoryAuditLogStore auditLogStore;
    private final InMemoryApprovalStore approvalStore;
    private final FeatureFlagCollector featureFlagCollector;
    private final FeatureFlagOverrideStore featureFlagOverrideStore;
    private final DependencyGraphCollector dependencyGraphCollector;
    private final FakeExternalServiceCollector fakeExternalServiceCollector;
    private final FakeExternalServiceStore fakeExternalServiceStore;
    private final RemoteSessionService remoteSessionService;
    private final DevToolsUiProperties properties;
    private final Clock clock;
    private final JobCollector jobCollector;
    private final DbQueryCollector dbQueryCollector;
    private final InMemoryLogStore logStore;
    private final WebhookSimulator webhookSimulator;
    private final ApiTestSimulator apiTestSimulator;

    DevToolsApiController(EndpointCollector endpointCollector,
                          RequestCaptureStore requestCaptureStore,
                          ConfigInspector configInspector,
                          JsonFileConfigSnapshotStore configSnapshotStore,
                          JsonFileFeatureFlagDefinitionStore featureFlagDefinitionStore,
                          InMemoryAuditLogStore auditLogStore,
                          InMemoryApprovalStore approvalStore,
                          FeatureFlagCollector featureFlagCollector,
                          FeatureFlagOverrideStore featureFlagOverrideStore,
                          DependencyGraphCollector dependencyGraphCollector,
                          FakeExternalServiceCollector fakeExternalServiceCollector,
                          FakeExternalServiceStore fakeExternalServiceStore,
                          RemoteSessionService remoteSessionService,
                          DevToolsUiProperties properties,
                          Clock clock,
                          JobCollector jobCollector,
                          DbQueryCollector dbQueryCollector,
                          InMemoryLogStore logStore,
                          WebhookSimulator webhookSimulator,
                          ApiTestSimulator apiTestSimulator) {
        this.endpointCollector = endpointCollector;
        this.requestCaptureStore = requestCaptureStore;
        this.configInspector = configInspector;
        this.configSnapshotStore = configSnapshotStore;
        this.featureFlagDefinitionStore = featureFlagDefinitionStore;
        this.auditLogStore = auditLogStore;
        this.approvalStore = approvalStore;
        this.featureFlagCollector = featureFlagCollector;
        this.featureFlagOverrideStore = featureFlagOverrideStore;
        this.dependencyGraphCollector = dependencyGraphCollector;
        this.fakeExternalServiceCollector = fakeExternalServiceCollector;
        this.fakeExternalServiceStore = fakeExternalServiceStore;
        this.remoteSessionService = remoteSessionService;
        this.properties = properties;
        this.clock = clock;
        this.jobCollector = jobCollector;
        this.dbQueryCollector = dbQueryCollector;
        this.logStore = logStore;
        this.webhookSimulator = webhookSimulator;
        this.apiTestSimulator = apiTestSimulator;
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/endpoints")
    PagedResponse<EndpointDescriptor> endpoints(@RequestParam(name = "q", required = false) String q,
                                                @RequestParam(name = "offset", required = false) Integer offset,
                                                @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isEndpoints()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                endpointCollector.collect(),
                q,
                offset,
                limit,
                endpoint -> matches(q, endpoint.method(), endpoint.path(), endpoint.controller(), endpoint.methodName())
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/requests")
    PagedResponse<CapturedRequest> requests(@RequestParam(name = "q", required = false) String q,
                                            @RequestParam(name = "offset", required = false) Integer offset,
                                            @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isRequests()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                requestCaptureStore.snapshot(),
                q,
                offset,
                limit,
                request -> matches(q, request.method(), request.path(), String.valueOf(request.responseStatus()), request.timestamp().toString())
        );
    }

    @PostMapping(DevToolsUiConstants.API_BASE_PATH + "/requests/replay")
    ErrorReplayResult replayErrorRequest(@RequestParam("requestId") String requestId) {
        requireEnabled(properties.getFeatures().isRequests(), "error replay");
        CapturedRequest capturedRequest = requestCaptureStore.findById(requestId);
        if (capturedRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown request artifact: " + requestId);
        }
        if (capturedRequest.responseStatus() < 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only 5xx request captures can be replayed");
        }

        ApiTestResult replay = apiTestSimulator.deliver(new ApiTestRequest(
                capturedRequest.method(),
                capturedRequest.path(),
                replayableBody(capturedRequest),
                flattenHeaders(capturedRequest.headers(), hasReplayableBody(capturedRequest))
        ));
        auditLogStore.record("requests", "request.error-replayed", actor(), requestId + " => " + replay.status());
        return new ErrorReplayResult(
                capturedRequest.requestId(),
                capturedRequest.method(),
                capturedRequest.path(),
                capturedRequest.responseStatus(),
                replay.status(),
                Instant.now(clock).toString(),
                replay.responseBody()
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/config")
    PagedResponse<ConfigPropertyDescriptor> config(@RequestParam(name = "q", required = false) String q,
                                                   @RequestParam(name = "offset", required = false) Integer offset,
                                                   @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isConfig()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                configInspector.inspect(),
                q,
                offset,
                limit,
                property -> matches(q, property.key(), property.value(), property.propertySource())
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/config/snapshots")
    PagedResponse<ConfigSnapshotDescriptor> configSnapshots(@RequestParam(name = "offset", required = false) Integer offset,
                                                            @RequestParam(name = "limit", required = false) Integer limit) {
        requireEnabled(properties.getFeatures().isConfig(), "config snapshots");
        return PagedResponseFactory.from(configSnapshotStore.snapshots(), null, offset, limit, item -> true);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/config/snapshots", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfigSnapshotDescriptor createConfigSnapshot(@RequestBody ConfigSnapshotRequest request) {
        requireEnabled(properties.getFeatures().isConfig(), "config snapshots");
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.CONFIG_WRITE);
        ConfigSnapshotDescriptor snapshot = configSnapshotStore.createSnapshot(request.label(), configInspector.inspect());
        auditLogStore.record("config", "config.snapshot.created", actor(), snapshot.label());
        return snapshot;
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/config/compare")
    ConfigComparisonDescriptor compareConfigSnapshot(@RequestParam("snapshotId") String snapshotId) {
        requireEnabled(properties.getFeatures().isConfig(), "config comparison");
        try {
            ConfigComparisonDescriptor comparison = configSnapshotStore.compare(snapshotId, configInspector.inspect());
            auditLogStore.record("config", "config.snapshot.compared", actor(), comparison.snapshot().label());
            return comparison;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/config/drift")
    ConfigDriftDescriptor configDrift(@RequestParam(name = "snapshotId", required = false) String snapshotId) {
        requireEnabled(properties.getFeatures().isConfig(), "config drift detection");
        try {
            ConfigDriftDescriptor drift = configSnapshotStore.drift(snapshotId, configInspector.inspect());
            if (drift.available() && drift.snapshot() != null) {
                auditLogStore.record("config", "config.drift.checked", actor(), drift.snapshot().label());
            }
            return drift;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/feature-flags")
    PagedResponse<FeatureFlagDescriptor> featureFlags(@RequestParam(name = "q", required = false) String q,
                                                      @RequestParam(name = "offset", required = false) Integer offset,
                                                      @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isFeatureFlags()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                featureFlagCollector.collect(),
                q,
                offset,
                limit,
                flag -> matches(
                        q,
                        flag.key(),
                        String.valueOf(flag.enabled()),
                        flag.propertySource(),
                        String.valueOf(flag.overridden()),
                        flag.definition() == null ? null : flag.definition().displayName(),
                        flag.definition() == null ? null : flag.definition().description(),
                        flag.definition() == null ? null : flag.definition().owner(),
                        flag.definition() == null ? null : String.join(" ", flag.definition().tags()),
                        flag.definition() == null ? null : flag.definition().lifecycle()
                )
        );
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/feature-flags", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeatureFlagDescriptor updateFeatureFlag(@RequestBody FeatureFlagUpdateRequest request) {
        requireEnabled(properties.getFeatures().isFeatureFlags(), "feature flags");
        requireFeatureFlagMutationAccess();
        FeatureFlagDefinitionDescriptor definition = featureFlagDefinitionStore.find(request.key());
        if (definition != null && !definition.allowOverride()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feature flag overrides are disabled for " + request.key());
        }
        featureFlagOverrideStore.set(request.key(), request.enabled());
        auditLogStore.record("feature-flags", "override.set", actor(), request.key() + "=" + request.enabled());
        return featureFlagCollector.get(request.key());
    }

    @DeleteMapping(DevToolsUiConstants.API_BASE_PATH + "/feature-flags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearFeatureFlag(@RequestParam("key") String key) {
        requireEnabled(properties.getFeatures().isFeatureFlags(), "feature flags");
        requireFeatureFlagMutationAccess();
        featureFlagOverrideStore.clear(key);
        auditLogStore.record("feature-flags", "override.cleared", actor(), key);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/feature-flags/definitions", consumes = MediaType.APPLICATION_JSON_VALUE)
    FeatureFlagDefinitionDescriptor saveFeatureFlagDefinition(@RequestBody FeatureFlagDefinitionUpdateRequest request) {
        requireEnabled(properties.getFeatures().isFeatureFlags(), "feature flags");
        requireFeatureFlagMutationAccess();
        FeatureFlagDefinitionDescriptor definition = featureFlagDefinitionStore.save(request, actor());
        auditLogStore.record("feature-flags", "definition.saved", actor(), definition.key());
        return definition;
    }

    @DeleteMapping(DevToolsUiConstants.API_BASE_PATH + "/feature-flags/definitions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFeatureFlagDefinition(@RequestParam("key") String key) {
        requireEnabled(properties.getFeatures().isFeatureFlags(), "feature flags");
        requireFeatureFlagMutationAccess();
        featureFlagDefinitionStore.delete(key);
        auditLogStore.record("feature-flags", "definition.deleted", actor(), key);
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/audit-logs")
    PagedResponse<AuditLogEventDescriptor> auditLogs(@RequestParam(name = "q", required = false) String q,
                                                     @RequestParam(name = "offset", required = false) Integer offset,
                                                     @RequestParam(name = "limit", required = false) Integer limit) {
        return PagedResponseFactory.from(
                auditLogStore.snapshot(),
                q,
                offset,
                limit,
                event -> matches(q, event.category(), event.action(), event.actor(), event.detail(), event.timestamp())
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/approvals")
    PagedResponse<ApprovalRequestDescriptor> approvals(@RequestParam(name = "q", required = false) String q,
                                                       @RequestParam(name = "offset", required = false) Integer offset,
                                                       @RequestParam(name = "limit", required = false) Integer limit) {
        return PagedResponseFactory.from(
                approvalStore.snapshot(),
                q,
                offset,
                limit,
                approval -> matches(
                        q,
                        approval.approvalId(),
                        approval.permission(),
                        approval.target(),
                        approval.reason(),
                        approval.requestedBy(),
                        approval.status(),
                        approval.approvedBy(),
                        approval.consumedBy()
                )
        );
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/approvals", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApprovalRequestDescriptor createApproval(@RequestBody ApprovalRequestCreateRequest request) {
        ApprovalRequestDescriptor approval = approvalStore.create(
                request.permission(),
                request.target(),
                request.reason(),
                actor(),
                properties.getAccess().getApproval().getTtlMinutes()
        );
        auditLogStore.record("approvals", "approval.requested", actor(), approval.permission() + ":" + approval.approvalId());
        return approval;
    }

    @PostMapping(DevToolsUiConstants.API_BASE_PATH + "/approvals/{approvalId}/approve")
    ApprovalRequestDescriptor approve(@PathVariable("approvalId") String approvalId) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.APPROVALS_REVIEW);
        ApprovalRequestDescriptor approval = approvalStore.approve(approvalId, actor());
        auditLogStore.record("approvals", "approval.approved", actor(), approval.permission() + ":" + approval.approvalId());
        return approval;
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/access/identity")
    AccessIdentityDescriptor accessIdentity() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return new AccessIdentityDescriptor(
                    properties.getAccess().getMode(),
                    false,
                    "local-operator",
                    "viewer",
                    null,
                    List.of(),
                    List.copyOf(DevToolsRbac.permissionsForRole("viewer", properties.getAccess()))
            );
        }
        Object groupsAttribute = attributes.getRequest().getAttribute(DevToolsAccessAttributes.GROUPS);
        @SuppressWarnings("unchecked")
        List<String> groups = groupsAttribute instanceof List<?> rawGroups ? (List<String>) rawGroups : List.of();
        String actor = requestAttribute(attributes, DevToolsAccessAttributes.ACTOR, "local-operator");
        String role = requestAttribute(attributes, DevToolsAccessAttributes.ROLE, "viewer");
        String email = requestAttribute(attributes, DevToolsAccessAttributes.EMAIL, null);
        return new AccessIdentityDescriptor(
                properties.getAccess().getMode(),
                true,
                actor,
                role,
                email,
                groups,
                List.copyOf(DevToolsRbac.permissions(properties.getAccess()))
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/fake-services")
    PagedResponse<FakeExternalServiceDescriptor> fakeServices(@RequestParam(name = "q", required = false) String q,
                                                              @RequestParam(name = "offset", required = false) Integer offset,
                                                              @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isFakeServices()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                fakeExternalServiceCollector.collect(),
                q,
                offset,
                limit,
                service -> matches(q, service.serviceId(), service.displayName(), service.description(), service.basePath(), String.join(" ", service.routes()))
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/session")
    PagedResponse<RemoteSessionDescriptor> session(@RequestParam(name = "offset", required = false) Integer offset,
                                                   @RequestParam(name = "limit", required = false) Integer limit) {
        return PagedResponseFactory.from(List.of(remoteSessionService.snapshot()), null, offset, limit, item -> true);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/attach", consumes = MediaType.APPLICATION_JSON_VALUE)
    RemoteSessionDescriptor attachSession(@RequestBody RemoteSessionAttachRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.attach", request.ownerName(), "allowGuests=" + request.allowGuests());
        return remoteSessionService.attach(request);
    }

    @PostMapping(DevToolsUiConstants.API_BASE_PATH + "/session/token")
    RemoteSessionDescriptor rotateSessionToken() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.token.rotated", actor(), "owner token rotated");
        return remoteSessionService.rotate();
    }

    @PostMapping(DevToolsUiConstants.API_BASE_PATH + "/session/heartbeat")
    RemoteSessionDescriptor heartbeatSession() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.heartbeat", actor(), "manual heartbeat");
        return remoteSessionService.heartbeat();
    }

    @PostMapping(DevToolsUiConstants.API_BASE_PATH + "/session/tunnel/open")
    RemoteSessionDescriptor openSessionTunnel() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.tunnel.open", actor(), "manual tunnel open");
        return remoteSessionService.openTunnel();
    }

    @PostMapping(DevToolsUiConstants.API_BASE_PATH + "/session/tunnel/close")
    RemoteSessionDescriptor closeSessionTunnel() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.tunnel.close", actor(), "manual tunnel close");
        return remoteSessionService.closeTunnel();
    }

    @PostMapping(DevToolsUiConstants.API_BASE_PATH + "/session/sync")
    RemoteSessionDescriptor syncSession() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.sync", actor(), "manual sync");
        return remoteSessionService.sync();
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/share", consumes = MediaType.APPLICATION_JSON_VALUE)
    SessionShareTokenDescriptor shareSession(@RequestBody SessionShareRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.share", actor(), "role=" + request.role());
        return remoteSessionService.issueShareToken(request);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    SessionAccessValidationDescriptor validateSessionAccess(@RequestBody SessionValidateRequest request) {
        return remoteSessionService.validateShareToken(request);
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/session/replay")
    PagedResponse<SessionReplayEntryDescriptor> sessionReplay(@RequestParam(name = "q", required = false) String q,
                                                              @RequestParam(name = "offset", required = false) Integer offset,
                                                              @RequestParam(name = "limit", required = false) Integer limit) {
        return PagedResponseFactory.from(
                remoteSessionService.replay(),
                q,
                offset,
                limit,
                entry -> matches(q, entry.category(), entry.title(), entry.payloadPreview(), entry.occurredAt())
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/session/hosted-view")
    HostedSessionViewDescriptor hostedSessionView() {
        return remoteSessionService.hostedView();
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/session/hosted-history")
    PagedResponse<HostedSessionViewDescriptor> hostedSessionHistory(@RequestParam(name = "offset", required = false) Integer offset,
                                                                    @RequestParam(name = "limit", required = false) Integer limit) {
        return PagedResponseFactory.from(remoteSessionService.hostedHistory(), null, offset, limit, item -> true);
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/session/viewer-sessions")
    PagedResponse<RelayViewerSessionDescriptor> viewerSessions(@RequestParam(name = "offset", required = false) Integer offset,
                                                               @RequestParam(name = "limit", required = false) Integer limit) {
        return PagedResponseFactory.from(remoteSessionService.viewerSessions(), null, offset, limit, item -> true);
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/session/identity")
    RelaySessionIdentityDescriptor sessionIdentity() {
        return remoteSessionService.sessionIdentity();
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/owner-transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
    RelaySessionIdentityDescriptor transferSessionOwner(@RequestBody SessionOwnerTransferRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.owner.transfer", actor(), request.targetViewerSessionId());
        return remoteSessionService.transferOwner(request);
    }

    @DeleteMapping(DevToolsUiConstants.API_BASE_PATH + "/session/viewer-sessions")
    PagedResponse<RelayViewerSessionDescriptor> revokeViewerSession(@RequestParam("viewerSessionId") String viewerSessionId,
                                                                    @RequestParam(name = "offset", required = false) Integer offset,
                                                                    @RequestParam(name = "limit", required = false) Integer limit) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.viewer.revoked", actor(), viewerSessionId);
        return PagedResponseFactory.from(remoteSessionService.revokeViewerSession(viewerSessionId), null, offset, limit, item -> true);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/hosted-view/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    HostedSessionViewDescriptor addHostedSessionMember(@RequestBody HostedSessionMemberRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.hosted-member.added", request.actor(), request.memberId() + ":" + request.role());
        return remoteSessionService.addHostedMember(request);
    }

    @DeleteMapping(DevToolsUiConstants.API_BASE_PATH + "/session/hosted-view/members")
    HostedSessionViewDescriptor removeHostedSessionMember(@RequestParam("memberId") String memberId,
                                                          @RequestParam(name = "actor", required = false) String actor) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.hosted-member.removed", actor, memberId);
        return remoteSessionService.removeHostedMember(memberId, actor);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/inspect", consumes = MediaType.APPLICATION_JSON_VALUE)
    RemoteSessionDescriptor inspectSessionArtifact(@RequestBody SessionInspectRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        return remoteSessionService.focusArtifact(request);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/notes", consumes = MediaType.APPLICATION_JSON_VALUE)
    RemoteSessionDescriptor addSessionDebugNote(@RequestBody SessionDebugNoteRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        return remoteSessionService.addDebugNote(request);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/recording/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    RemoteSessionDescriptor startSessionRecording(@RequestBody SessionRecordingRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        return remoteSessionService.startRecording(request);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/session/recording/stop", consumes = MediaType.APPLICATION_JSON_VALUE)
    RemoteSessionDescriptor stopSessionRecording(@RequestBody SessionRecordingRequest request) {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        return remoteSessionService.stopRecording(request);
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/session/artifacts/request")
    CapturedRequest requestArtifact(@RequestParam("requestId") String requestId) {
        CapturedRequest capturedRequest = requestCaptureStore.findById(requestId);
        if (capturedRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown request artifact: " + requestId);
        }
        return capturedRequest;
    }

    @DeleteMapping(DevToolsUiConstants.API_BASE_PATH + "/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.SESSION_CONTROL);
        auditLogStore.record("session", "session.revoked", actor(), "local session revoked");
        remoteSessionService.revoke();
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/fake-services", consumes = MediaType.APPLICATION_JSON_VALUE)
    FakeExternalServiceDescriptor updateFakeService(@RequestBody FakeExternalServiceUpdateRequest request) {
        requireEnabled(properties.getFeatures().isFakeServices(), "fake external services");
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.FAKE_SERVICES_WRITE);
        fakeExternalServiceStore.setEnabled(request.serviceId(), request.enabled());
        if (request.mockResponse() != null) {
            fakeExternalServiceStore.setMockResponse(
                    request.mockResponse().routeId(),
                    new com.devtools.ui.core.fakes.FakeExternalServiceMockResponse(
                            request.mockResponse().status(),
                            request.mockResponse().contentType() == null || request.mockResponse().contentType().isBlank()
                                    ? "application/json"
                                    : request.mockResponse().contentType().trim(),
                            request.mockResponse().body() == null ? "" : request.mockResponse().body()
                    )
            );
            auditLogStore.record(
                    "fake-services",
                    "mock.updated",
                    actor(),
                    request.serviceId() + ":" + request.mockResponse().routeId() + ":" + request.mockResponse().status()
            );
        }
        auditLogStore.record("fake-services", "service.toggled", actor(), request.serviceId() + "=" + request.enabled());
        return fakeExternalServiceCollector.collect().stream()
                .filter(service -> service.serviceId().equals(request.serviceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown fake service: " + request.serviceId()));
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/time")
    PagedResponse<TimeTravelStateDescriptor> time(@RequestParam(name = "offset", required = false) Integer offset,
                                                  @RequestParam(name = "limit", required = false) Integer limit) {
        return PagedResponseFactory.from(
                List.of(currentTimeState()),
                null,
                offset,
                limit,
                item -> true
        );
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/time", consumes = MediaType.APPLICATION_JSON_VALUE)
    TimeTravelStateDescriptor updateTime(@RequestBody TimeTravelRequest request) {
        requireEnabled(properties.getFeatures().isTime(), "time travel");
        requireTimeTravelMutationAccess();
        requireApprovalIfNeeded(DevToolsPermission.TIME_WRITE);
        if (!(clock instanceof MutableDevToolsClock mutableDevToolsClock)) {
            throw new IllegalStateException("Dev tools clock overrides require the auto-configured mutable clock");
        }

        ZoneId zoneId = request.zoneId() == null || request.zoneId().isBlank()
                ? clock.getZone()
                : ZoneId.of(request.zoneId());
        mutableDevToolsClock.set(Instant.parse(request.instant()), zoneId, actor(), request.reason(), request.durationMinutes());
        auditLogStore.record(
                "time",
                "time.override.set",
                actor(),
                request.instant() + "@" + zoneId + (request.durationMinutes() == null ? "" : " for " + request.durationMinutes() + "m")
        );
        return currentTimeState();
    }

    @DeleteMapping(DevToolsUiConstants.API_BASE_PATH + "/time")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetTime() {
        requireEnabled(properties.getFeatures().isTime(), "time travel");
        requireTimeTravelMutationAccess();
        if (clock instanceof MutableDevToolsClock mutableDevToolsClock) {
            mutableDevToolsClock.reset();
        }
        auditLogStore.record("time", "time.override.reset", actor(), "system clock restored");
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/dependencies")
    PagedResponse<DependencyNodeDescriptor> dependencies(@RequestParam(name = "q", required = false) String q,
                                                         @RequestParam(name = "offset", required = false) Integer offset,
                                                         @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isDependencies()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                dependencyGraphCollector.collect(),
                q,
                offset,
                limit,
                node -> matches(
                        q,
                        node.beanName(),
                        node.beanType(),
                        node.scope(),
                        String.join(" ", node.dependencies()),
                        String.join(" ", node.dependents())
                )
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/jobs")
    PagedResponse<JobDescriptor> jobs(@RequestParam(name = "q", required = false) String q,
                                      @RequestParam(name = "offset", required = false) Integer offset,
                                      @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isJobs()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                jobCollector.collect(),
                q,
                offset,
                limit,
                job -> matches(q, job.beanName(), job.beanType(), job.methodName(), job.triggerType(), job.expression(), job.scheduler())
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/webhooks/targets")
    PagedResponse<WebhookTargetDescriptor> webhookTargets(@RequestParam(name = "q", required = false) String q,
                                                          @RequestParam(name = "offset", required = false) Integer offset,
                                                          @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isWebhooks()) {
            return emptyPage(q, offset, limit);
        }
        List<WebhookTargetDescriptor> targets = endpointCollector.collect().stream()
                .filter(endpoint -> List.of("POST", "PUT", "PATCH").contains(endpoint.method()))
                .map(endpoint -> new WebhookTargetDescriptor(
                        endpoint.method(),
                        endpoint.path(),
                        endpoint.controller(),
                        endpoint.methodName()
                ))
                .toList();

        return PagedResponseFactory.from(
                targets,
                q,
                offset,
                limit,
                target -> matches(q, target.method(), target.path(), target.controller(), target.methodName())
        );
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/db-queries")
    PagedResponse<DbQueryDescriptor> dbQueries(@RequestParam(name = "q", required = false) String q,
                                               @RequestParam(name = "offset", required = false) Integer offset,
                                               @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isDbQueries()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                dbQueryCollector.collect(),
                q,
                offset,
                limit,
                query -> matches(q, query.sql(), query.statementType(), query.dataSource(), String.valueOf(query.rowsAffected()))
        );
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/webhooks/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    WebhookDeliveryResult sendWebhook(@RequestBody WebhookDeliveryRequest request) {
        requireEnabled(properties.getFeatures().isWebhooks(), "webhook simulator");
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.WEBHOOKS_SEND);
        requireApprovalIfNeeded(DevToolsPermission.WEBHOOKS_SEND);
        auditLogStore.record("webhooks", "webhook.sent", actor(), request.path());
        return webhookSimulator.deliver(request);
    }

    @PostMapping(path = DevToolsUiConstants.API_BASE_PATH + "/api-testing/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiTestResult sendApiTest(@RequestBody ApiTestRequest request) {
        requireEnabled(properties.getFeatures().isWebhooks(), "api testing");
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.API_TESTING_SEND);
        requireApprovalIfNeeded(DevToolsPermission.API_TESTING_SEND);
        auditLogStore.record("api-testing", "api-test.sent", actor(), request.method() + " " + request.path());
        return apiTestSimulator.deliver(request);
    }

    private String actor() {
        return DevToolsRbac.actor();
    }

    private void requireFeatureFlagMutationAccess() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.FEATURE_FLAGS_WRITE);
    }

    private Map<String, String> flattenHeaders(Map<String, List<String>> headers, boolean includeBodyHeaders) {
        Map<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (values == null || values.isEmpty() || shouldSkipReplayHeader(name, includeBodyHeaders)) {
                return;
            }
            flattened.put(name, values.get(0));
        });
        return flattened;
    }

    private boolean shouldSkipReplayHeader(String headerName, boolean includeBodyHeaders) {
        return "host".equalsIgnoreCase(headerName)
                || "content-length".equalsIgnoreCase(headerName)
                || "connection".equalsIgnoreCase(headerName)
                || "accept-encoding".equalsIgnoreCase(headerName)
                || (!includeBodyHeaders && "content-type".equalsIgnoreCase(headerName));
    }

    private boolean hasReplayableBody(CapturedRequest request) {
        return !request.binaryBody() && !request.bodyTruncated() && request.body() != null && !request.body().isBlank();
    }

    private String replayableBody(CapturedRequest request) {
        return hasReplayableBody(request) ? request.body() : "";
    }

    @GetMapping(DevToolsUiConstants.API_BASE_PATH + "/logs")
    PagedResponse<LogEventDescriptor> logs(@RequestParam(name = "q", required = false) String q,
                                           @RequestParam(name = "level", required = false) String level,
                                           @RequestParam(name = "logger", required = false) String logger,
                                           @RequestParam(name = "offset", required = false) Integer offset,
                                           @RequestParam(name = "limit", required = false) Integer limit) {
        if (!properties.getFeatures().isLogs()) {
            return emptyPage(q, offset, limit);
        }
        return PagedResponseFactory.from(
                logStore.snapshot(),
                q,
                offset,
                limit,
                log -> matches(q, log.level(), log.logger(), log.message(), log.timestamp().toString(), log.stackTrace())
                        && matches(level, log.level())
                        && matches(logger, log.logger())
        );
    }

    private boolean matches(String rawQuery, String... values) {
        String normalizedQuery = PagedResponseFactory.normalizeQuery(rawQuery);
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        for (String value : values) {
            if (value != null && PagedResponseFactory.containsIgnoreCase(value, normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    private TimeTravelStateDescriptor currentTimeState() {
        boolean overridden = properties.getFeatures().isTime()
                && clock instanceof MutableDevToolsClock mutableDevToolsClock
                && mutableDevToolsClock.isOverridden();
        return new TimeTravelStateDescriptor(
                clock.instant().toString(),
                clock.getZone().getId(),
                overridden,
                overridden && clock instanceof MutableDevToolsClock mutableDevToolsClock ? mutableDevToolsClock.overrideReason() : null,
                overridden && clock instanceof MutableDevToolsClock mutableDevToolsClock ? mutableDevToolsClock.overriddenBy() : null,
                overridden && clock instanceof MutableDevToolsClock mutableDevToolsClock ? mutableDevToolsClock.overriddenAt() : null,
                overridden && clock instanceof MutableDevToolsClock mutableDevToolsClock ? mutableDevToolsClock.expiresAt() : null
        );
    }

    private void requireTimeTravelMutationAccess() {
        DevToolsRbac.require(properties.getAccess(), DevToolsPermission.TIME_WRITE);
    }

    private void requireApprovalIfNeeded(DevToolsPermission permission) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String approvalId = attributes == null ? null : attributes.getRequest().getHeader("X-DevTools-Approval");
        approvalStore.consumeIfRequired(permission, approvalId, actor(), properties.getAccess());
    }

    private String requestAttribute(ServletRequestAttributes attributes, String name, String defaultValue) {
        Object value = attributes.getRequest().getAttribute(name);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : defaultValue;
    }

    private <T> PagedResponse<T> emptyPage(String query, Integer offset, Integer limit) {
        return PagedResponseFactory.from(List.of(), query, offset, limit, item -> false);
    }

    private void requireEnabled(boolean enabled, String featureName) {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, featureName + " is disabled");
        }
    }
}
