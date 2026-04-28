package com.devtools.ui.relay;

import com.devtools.ui.core.model.CapturedRequest;
import com.devtools.ui.core.model.RelayAccountDescriptor;
import com.devtools.ui.core.model.RelayOrganizationDescriptor;
import com.devtools.ui.core.model.RelaySessionIdentityDescriptor;
import com.devtools.ui.core.model.HostedSessionViewDescriptor;
import com.devtools.ui.core.model.RelayViewerSessionDescriptor;
import com.devtools.ui.core.model.SessionActivityEventDescriptor;
import com.devtools.ui.core.model.SessionAuditEventDescriptor;
import com.devtools.ui.core.model.SessionDebugNoteDescriptor;
import com.devtools.ui.core.model.SessionRecordingDescriptor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class InMemoryRelaySessionStore {

    private final RelayServerProperties properties;
    private final ObjectMapper objectMapper;
    private final Path persistenceFile;
    private final RelayAuthTokenService tokenService;
    private final Map<String, RelaySessionRecord> sessions = new ConcurrentHashMap<>();
    private final Map<String, RelayOrganizationDescriptor> organizations = new ConcurrentHashMap<>();
    private final Map<String, RelayAccountDescriptor> accounts = new ConcurrentHashMap<>();
    private final Map<String, RelayAccountSession> accountSessions = new ConcurrentHashMap<>();
    private final Map<String, RelayInvitation> invitations = new ConcurrentHashMap<>();
    private final Map<String, RelayEntitlement> entitlements = new ConcurrentHashMap<>();
    private final Map<String, RelayProject> projects = new ConcurrentHashMap<>();
    private final Map<String, String> revokedTokenJtis = new ConcurrentHashMap<>();
    private final Map<String, RelayWebIdentityBinding> webIdentityBindings = new ConcurrentHashMap<>();
    private final List<SessionAuditEventDescriptor> auditEvents = new ArrayList<>();

    record RelayAccountSessionContext(RelayAccountDescriptor account,
                                      String accountId,
                                      String organizationId,
                                      String role,
                                      String sessionExpiresAt,
                                      Instant tokenExpiresAt,
                                      String jti) {
    }

    InMemoryRelaySessionStore(RelayServerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.persistenceFile = Path.of(properties.persistenceFile());
        this.tokenService = new RelayAuthTokenService(properties);
        loadState();
    }

    synchronized RelayServerStatusResponse status() {
        int attachedSessionCount = 0;
        int viewerSessionCount = 0;
        int tunnelCount = 0;
        for (RelaySessionRecord record : sessions.values()) {
            if (record.connectionId != null) {
                attachedSessionCount += 1;
            }
            viewerSessionCount += record.viewerSessions.size();
            if (record.tunnelId != null && !"closed".equals(record.tunnelStatus)) {
                tunnelCount += 1;
            }
        }
        return new RelayServerStatusResponse(
                "ok",
                properties.publicBaseUrl(),
                persistenceFile.toString(),
                Files.exists(persistenceFile),
                sessions.size(),
                organizations.size(),
                accounts.size(),
                attachedSessionCount,
                viewerSessionCount,
                tunnelCount
        );
    }

    synchronized Object exportState() {
        return snapshotState();
    }

    synchronized RelayAdminImportResponse importState(Object snapshot) {
        recordAudit("relay.admin.import", "relay-admin", "import relay state snapshot", null, null);
        RelayStateSnapshot state = objectMapper.convertValue(snapshot, RelayStateSnapshot.class);
        sessions.clear();
        organizations.clear();
        accounts.clear();
        accountSessions.clear();
        invitations.clear();
        entitlements.clear();
        projects.clear();
        revokedTokenJtis.clear();
        webIdentityBindings.clear();
        auditEvents.clear();
        loadSnapshots(
                state.sessions(),
                state.organizations(),
                state.accounts(),
                state.accountSessions(),
                state.invitations(),
                state.entitlements(),
                state.projects(),
                state.revokedTokenJtis(),
                state.webIdentityBindings(),
                state.auditEvents()
        );
        saveState();
        return new RelayAdminImportResponse(
                "imported",
                sessions.size(),
                organizations.size(),
                accounts.size()
        );
    }

    synchronized RelayAttachResponse attach(RelayAttachPayload payload) {
        RelaySessionRecord record = sessions.computeIfAbsent(
                payload.sessionId(),
                sessionId -> RelaySessionRecord.create(sessionId)
        );
        String now = Instant.now().toString();
        record.ownerName = payload.ownerName();
        RelayOrganizationDescriptor organization = upsertOrganization(payload.ownerName());
        RelayQuotaResponse quota = quotaFor(organization.organizationId());
        enforceQuota(organization.organizationId(), quota.maxSessions(), quota.sessionCount(), "Organization has reached max hosted sessions");
        RelayAccountDescriptor ownerAccount = upsertAccount("acct-" + slug(payload.ownerName()), payload.ownerName(), organization.organizationId(), "owner");
        record.organizationId = organization.organizationId();
        record.organizationName = organization.organizationName();
        record.ownerAccountId = ownerAccount.accountId();
        record.allowedRoles = payload.allowedRoles() == null ? List.of() : List.copyOf(payload.allowedRoles());
        record.relayUrl = payload.relayUrl();
        record.shareUrl = payload.shareUrl();
        record.encryptedToken = payload.encryptedToken();
        record.expiresAt = payload.expiresAt();
        record.handshakeId = UUID.randomUUID().toString();
        record.connectionId = UUID.randomUUID().toString();
        record.leaseId = UUID.randomUUID().toString();
        record.leaseExpiresAt = Instant.now().plus(properties.leaseTtl()).toString();
        record.relayStatus = "connected";
        record.tunnelStatus = "closed";
        record.lastAttachedAt = now;
        record.accessTokens.clear();
        record.viewerSessions.clear();
        recordAudit("relay.session.attach", payload.ownerName(), "attach relay session", record.organizationId, record.sessionId);
        saveState();
        return new RelayAttachResponse(
                record.handshakeId,
                record.connectionId,
                record.relayStatus,
                record.leaseId,
                record.leaseExpiresAt,
                viewerUrl(record.sessionId),
                record.organizationId,
                record.organizationName,
                record.ownerAccountId
        );
    }

    synchronized String bootstrapWebIdentity(RelayWebIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("Missing web identity");
        }
        String key = RelayWebIdentityBinding.key(identity.provider(), identity.subject());
        RelayWebIdentityBinding existing = webIdentityBindings.get(key);
        RelayWebIdentityBinding binding = existing;
        String now = Instant.now().toString();
        if (binding == null) {
            // New web user: create a dedicated org and owner account. This is free/alpha behaviour but shaped
            // like an enterprise product: stable org + membership derived from identity.
            String base = identity.login() == null || identity.login().isBlank()
                    ? identity.displayName()
                    : identity.login();
            String orgId = "org-" + slug(base);
            String orgName = (identity.displayName() == null || identity.displayName().isBlank() ? "Workspace" : identity.displayName().trim()) + " Workspace";
            // Avoid collisions across different GitHub users with same slug.
            if (organizations.containsKey(orgId)) {
                orgId = orgId + "-" + slug(identity.subject()).substring(0, Math.min(8, slug(identity.subject()).length()));
            }
            organizations.putIfAbsent(orgId, new RelayOrganizationDescriptor(orgId, orgName));
            String accountId = "acct-" + slug(base);
            if (accounts.containsKey(accountId) && !orgId.equals(accounts.get(accountId).organizationId())) {
                accountId = accountId + "-" + slug(identity.subject()).substring(0, Math.min(8, slug(identity.subject()).length()));
            }
            upsertAccount(accountId, identity.displayName(), orgId, "owner");
            binding = new RelayWebIdentityBinding(identity.provider(), identity.subject(), orgId, accountId, now, now);
            webIdentityBindings.put(key, binding);
            recordAudit("relay.web.identity.bound", identity.displayName(), "web identity bound to " + accountId, orgId, null);
        } else {
            webIdentityBindings.put(key, binding.touch(now));
        }

        Instant expiresAt = Instant.now().plus(properties.leaseTtl());
        RelayAuthTokenService.RelayAccountSessionToken issued = tokenService.issueAccountSession(
                binding.accountId(),
                binding.organizationId(),
                "owner",
                expiresAt
        );
        accountSessions.put(issued.jti(), new RelayAccountSession(binding.accountId(), expiresAt.toString()));
        saveState();
        return issued.token();
    }

    synchronized RelayHeartbeatResponse heartbeat(RelayHeartbeatPayload payload) {
        RelaySessionRecord record = requireSession(payload.sessionId());
        ensureConnection(record, payload.connectionId());
        record.relayStatus = "connected";
        record.leaseExpiresAt = Instant.now().plus(properties.leaseTtl()).toString();
        sendTunnelEvent(record, "heartbeat", record.leaseExpiresAt);
        saveState();
        return new RelayHeartbeatResponse(
                record.relayStatus,
                record.tunnelStatus,
                true,
                record.leaseId,
                record.leaseExpiresAt
        );
    }

    synchronized RelaySyncResponse sync(RelaySyncPayload payload) {
        RelaySessionRecord record = requireSession(payload.sessionId());
        ensureConnection(record, payload.connectionId());
        record.lastSyncId = UUID.randomUUID().toString();
        record.relayStatus = "synced";
        if (payload.snapshot() != null) {
            record.sessionVersion = payload.snapshot().sessionVersion();
            record.ownerName = payload.snapshot().ownerName();
        }
        String viewId = record.currentView != null ? record.currentView.viewId() : UUID.randomUUID().toString();
        sendTunnelEvent(record, "sync", record.lastSyncId);
        saveState();
        return new RelaySyncResponse(record.lastSyncId, viewId, "synced", true);
    }

    synchronized RelayTunnelOpenResponse openTunnel(RelayTunnelOpenPayload payload) {
        RelaySessionRecord record = requireSession(payload.sessionId());
        ensureConnection(record, payload.connectionId());
        record.tunnelId = UUID.randomUUID().toString();
        record.tunnelStatus = "open";
        record.tunnelOpenedAt = Instant.now().toString();
        record.tunnelClosedAt = null;
        saveState();
        return new RelayTunnelOpenResponse(
                record.tunnelId,
                record.tunnelStatus,
                record.tunnelOpenedAt,
                streamUrl(record.tunnelId, record.connectionId)
        );
    }

    synchronized RelayTunnelCloseResponse closeTunnel(RelayTunnelClosePayload payload) {
        RelaySessionRecord record = requireSession(payload.sessionId());
        ensureConnection(record, payload.connectionId());
        if (record.tunnelId == null || !record.tunnelId.equals(payload.tunnelId())) {
            throw new IllegalArgumentException("Unknown tunnelId for session " + payload.sessionId());
        }
        record.tunnelStatus = "closed";
        record.tunnelClosedAt = Instant.now().toString();
        sendTunnelEvent(record, "tunnel-closed", record.tunnelStatus);
        closeEmitter(record);
        saveState();
        return new RelayTunnelCloseResponse(record.tunnelStatus, record.tunnelClosedAt);
    }

    synchronized SseEmitter openTunnelStream(String tunnelId, String connectionId) {
        RelaySessionRecord record = requireTunnel(tunnelId);
        ensureConnection(record, connectionId);
        SseEmitter emitter = new SseEmitter(0L);
        record.emitter = emitter;
        emitter.onCompletion(() -> removeEmitter(record, emitter));
        emitter.onTimeout(() -> removeEmitter(record, emitter));
        emitter.onError(ignored -> removeEmitter(record, emitter));
        sendTunnelEvent(record, "connected", record.sessionId);
        return emitter;
    }

    synchronized HostedSessionViewDescriptor currentHostedView(String sessionId) {
        RelaySessionRecord record = requireSession(sessionId);
        return record.currentView;
    }

    synchronized List<HostedSessionViewDescriptor> hostedHistory(String sessionId) {
        RelaySessionRecord record = requireSession(sessionId);
        return new ArrayList<>(record.history);
    }

    synchronized void storePublishedView(String sessionId, HostedSessionViewDescriptor view) {
        RelaySessionRecord record = requireSession(sessionId);
        record.currentView = currentize(view);
        record.history.addFirst(view);
        while (record.history.size() > properties.hostedHistoryLimit()) {
            record.history.removeLast();
        }
        record.lastPublishedAt = Instant.now().toString();
        saveState();
    }

    synchronized void storeCurrentView(String sessionId, HostedSessionViewDescriptor view) {
        RelaySessionRecord record = requireSession(sessionId);
        record.currentView = currentize(view);
        saveState();
    }

    synchronized void clearHostedView(String sessionId) {
        RelaySessionRecord record = requireSession(sessionId);
        record.currentView = null;
        record.history.clear();
        saveState();
    }

    synchronized void registerAccessToken(RelayAccessTokenPayload payload) {
        RelaySessionRecord record = requireSession(payload.sessionId());
        if (payload.token() == null || payload.token().isBlank()) {
            throw new IllegalArgumentException("Missing relay access token");
        }
        record.accessTokens.put(
                payload.token(),
                new RelayAccessToken(
                        payload.role() == null || payload.role().isBlank() ? "viewer" : payload.role().trim(),
                        parseInstant(payload.expiresAt())
                )
        );
        saveState();
    }

    synchronized RelayViewerSessionResponse createViewerSession(String sessionId, RelayViewerSessionRequest request) {
        RelaySessionRecord record = requireSession(sessionId);
        ensureTeamEntitlement(record.organizationId);
        RelayQuotaResponse quota = quotaFor(record.organizationId);
        enforceQuota(record.organizationId, quota.maxViewerSessions(), quota.viewerSessionCount(), "Organization has reached max viewer sessions");
        String viewerName = request.viewerName() == null || request.viewerName().isBlank()
                ? "shared-viewer"
                : request.viewerName().trim();
        String viewerSessionId = "viewer_" + UUID.randomUUID();
        RelayViewerAccess access = viewerAccess(record, sessionId, request, viewerSessionId);
        Instant expiresAt = Instant.parse(access.expiresAt());
        RelayAuthTokenService.RelayViewerSessionToken issued = tokenService.issueViewerSession(
                viewerSessionId,
                sessionId,
                record.organizationId,
                access.account().accountId(),
                access.role(),
                viewerName,
                expiresAt
        );
        record.viewerSessions.put(
                issued.jti(),
                new RelayViewerSession(
                        issued.jti(),
                        access.account().accountId(),
                        access.role(),
                        access.expiresAt(),
                        access.account().displayName(),
                        Instant.now().toString()
                )
        );
        recordAudit("relay.viewer-session.created", access.account().displayName(), "viewer session " + issued.jti() + " created", record.organizationId, sessionId);
        saveState();
        return new RelayViewerSessionResponse(issued.jti(), issued.token(), access.role(), access.expiresAt());
    }

    synchronized RelayAccessGrant validateAccessToken(String sessionId, String token) {
        RelaySessionRecord record = requireSession(sessionId);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing relay access token");
        }
        pruneExpiredTokens(record);
        RelayAccessToken accessToken = record.accessTokens.get(token);
        if (accessToken == null) {
            throw new IllegalArgumentException("Invalid relay access token");
        }
        return new RelayAccessGrant(accessToken.role(), accessToken.expiresAt().toString());
    }

    synchronized RelayViewerSessionGrant validateViewerSession(String sessionId, String viewerSessionId) {
        RelaySessionRecord record = requireSession(sessionId);
        if (viewerSessionId == null || viewerSessionId.isBlank()) {
            throw new IllegalArgumentException("Missing relay viewer session");
        }
        // Accept JWT viewer session tokens; legacy opaque ids are still accepted for alpha data.
        String lookupId = viewerSessionId;
        if (viewerSessionId.contains(".")) {
            RelayAuthTokenService.RelayViewerSessionClaims claims = tokenService.verifyViewerSession(viewerSessionId);
            if (!sessionId.equals(claims.sessionId())) {
                throw new RelayAuthException("Invalid relay viewer session");
            }
            if (isRevoked(claims.jti())) {
                throw new RelayAuthException("Invalid relay viewer session");
            }
            lookupId = claims.jti();
        }
        pruneExpiredViewerSessions(record);
        RelayViewerSession viewerSession = record.viewerSessions.get(lookupId);
        if (viewerSession == null) {
            throw new RelayAuthException("Invalid relay viewer session");
        }
        return new RelayViewerSessionGrant(
                viewerSession.role(),
                viewerSession.expiresAt(),
                viewerSession.viewerName(),
                viewerSession.createdAt()
        );
    }

    synchronized List<RelayViewerSessionDescriptor> viewerSessions(String sessionId, String connectionId) {
        RelaySessionRecord record = requireSession(sessionId);
        ensureConnection(record, connectionId);
        pruneExpiredViewerSessions(record);
        return record.viewerSessions.values().stream()
                .map(session -> new RelayViewerSessionDescriptor(
                        session.viewerSessionId(),
                        session.role(),
                        session.viewerName(),
                        session.createdAt(),
                        session.expiresAt()
                ))
                .toList();
    }

    synchronized void revokeViewerSession(String sessionId, String connectionId, String viewerSessionId) {
        RelaySessionRecord record = requireSession(sessionId);
        ensureConnection(record, connectionId);
        String revokeId = viewerSessionId;
        if (viewerSessionId != null && viewerSessionId.contains(".")) {
            RelayAuthTokenService.RelayViewerSessionClaims claims = tokenService.verifyViewerSession(viewerSessionId);
            revokeId = claims.jti();
        }
        if (revokeId != null) {
            record.viewerSessions.remove(revokeId);
            revokeToken(revokeId, Instant.now().plus(properties.leaseTtl()));
        }
        recordAudit("relay.viewer-session.revoked", record.ownerName, "viewer session " + revokeId + " revoked", record.organizationId, sessionId);
        saveState();
    }

    synchronized RelaySessionIdentityDescriptor sessionIdentity(String sessionId, String connectionId) {
        RelaySessionRecord record = requireSession(sessionId);
        ensureConnection(record, connectionId);
        return new RelaySessionIdentityDescriptor(
                sessionId,
                organizations.getOrDefault(record.organizationId, new RelayOrganizationDescriptor(record.organizationId, record.organizationName)),
                accounts.getOrDefault(record.ownerAccountId, new RelayAccountDescriptor(record.ownerAccountId, record.ownerName, record.organizationId, "owner")),
                record.viewerSessions.values().stream()
                        .map(session -> accounts.getOrDefault(
                                session.accountId(),
                                new RelayAccountDescriptor(session.accountId(), session.viewerName(), record.organizationId, session.role())
                        ))
                        .toList()
        );
    }

    synchronized RelaySessionIdentityDescriptor transferOwner(String sessionId, RelayOwnerTransferRequest request) {
        RelaySessionRecord record = requireSession(sessionId);
        ensureConnection(record, request.connectionId());
        RelayViewerSession viewerSession = record.viewerSessions.get(request.targetViewerSessionId());
        if (viewerSession == null) {
            throw new IllegalArgumentException("Unknown target viewer session");
        }
        RelayAccountDescriptor ownerAccount = upsertAccount(viewerSession.accountId(), viewerSession.viewerName(), record.organizationId, "owner");
        record.ownerAccountId = ownerAccount.accountId();
        record.ownerName = viewerSession.viewerName();
        recordAudit("relay.owner.transfer", viewerSession.viewerName(), "ownership transferred to " + ownerAccount.accountId(), record.organizationId, sessionId);
        saveState();
        return sessionIdentity(sessionId, request.connectionId());
    }

    synchronized List<RelayOrganizationDescriptor> organizations() {
        return organizations.values().stream()
                .sorted(java.util.Comparator.comparing(RelayOrganizationDescriptor::organizationId))
                .toList();
    }

    synchronized List<RelayAccountDescriptor> accounts(String organizationId) {
        return accounts.values().stream()
                .filter(account -> organizationId == null || organizationId.isBlank() || organizationId.equals(account.organizationId()))
                .sorted(java.util.Comparator.comparing(RelayAccountDescriptor::accountId))
                .toList();
    }

    synchronized RelayDirectoryResponse scopedDirectory(String accountSessionToken) {
        RelayAccountDescriptor caller = requireDashboardAccount(accountSessionToken);
        RelayOrganizationDescriptor organization = requireOrganization(caller.organizationId());
        return new RelayDirectoryResponse(
                List.of(organization),
                accounts.values().stream()
                        .filter(account -> organization.organizationId().equals(account.organizationId()))
                        .sorted(java.util.Comparator.comparing(RelayAccountDescriptor::accountId))
                        .toList()
        );
    }

    synchronized RelayOrganizationDescriptor upsertOrganization(RelayOrganizationRequest request) {
        String organizationName = request.organizationName() == null || request.organizationName().isBlank()
                ? "Shared Workspace"
                : request.organizationName().trim();
        String organizationId = request.organizationId() == null || request.organizationId().isBlank()
                ? "org-" + slug(organizationName)
                : request.organizationId().trim();
        RelayOrganizationDescriptor organization = new RelayOrganizationDescriptor(organizationId, organizationName);
        organizations.put(organizationId, organization);
        saveState();
        return organization;
    }

    synchronized RelayAccountDescriptor upsertAccount(RelayAccountRequest request) {
        String organizationId = request.organizationId() == null || request.organizationId().isBlank()
                ? "org-shared-workspace"
                : request.organizationId().trim();
        organizations.putIfAbsent(organizationId, new RelayOrganizationDescriptor(organizationId, "Shared Workspace"));
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? "shared-viewer"
                : request.displayName().trim();
        String accountId = request.accountId() == null || request.accountId().isBlank()
                ? "acct-" + slug(displayName)
                : request.accountId().trim();
        RelayAccountDescriptor account = upsertAccount(accountId, displayName, organizationId, request.role());
        saveState();
        return account;
    }

    synchronized RelayEntitlementResponse entitlement(String organizationId) {
        requireOrganization(organizationId);
        return entitlementFor(organizationId).response(organizationId);
    }

    synchronized RelayEntitlementResponse upsertEntitlement(String organizationId, RelayEntitlementRequest request) {
        requireOrganization(organizationId);
        String plan = request.plan() == null || request.plan().isBlank() ? "free" : request.plan().trim();
        String status = request.status() == null || request.status().isBlank() ? "inactive" : request.status().trim();
        int seatLimit = request.seatLimit() == null || request.seatLimit() < 0 ? 0 : request.seatLimit();
        RelayEntitlement entitlement = new RelayEntitlement(plan, status, seatLimit);
        entitlements.put(organizationId, entitlement);
        recordAudit("relay.entitlement.updated", "relay-admin", "entitlement " + plan + "/" + status, organizationId, null);
        saveState();
        return entitlement.response(organizationId);
    }

    synchronized RelayAccountLoginResponse loginAccount(RelayAccountLoginRequest request) {
        if (request.accountId() == null || request.accountId().isBlank()) {
            throw new IllegalArgumentException("Missing relay account id");
        }
        RelayAccountDescriptor account = accounts.get(request.accountId().trim());
        if (account == null) {
            throw new IllegalArgumentException("Unknown relay account. Accept an invitation or attach a session to bootstrap an owner account first.");
        }
        if (request.organizationId() != null
                && !request.organizationId().isBlank()
                && !request.organizationId().trim().equals(account.organizationId())) {
            throw new IllegalArgumentException("Account " + account.accountId() + " is not a member of organization " + request.organizationId().trim());
        }
        Instant expiresAt = Instant.now().plus(properties.leaseTtl());
        RelayAuthTokenService.RelayAccountSessionToken issued = tokenService.issueAccountSession(
                account.accountId(),
                account.organizationId(),
                account.role(),
                expiresAt
        );
        accountSessions.put(issued.jti(), new RelayAccountSession(account.accountId(), expiresAt.toString()));
        recordAudit("relay.account.login", account.displayName(), "account session issued", account.organizationId(), null);
        saveState();
        return new RelayAccountLoginResponse(issued.token(), expiresAt.toString(), account);
    }

    synchronized RelayAccountDescriptor directoryUpsertAccount(String accountSessionToken, RelayAccountRequest request) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? "shared-viewer"
                : request.displayName().trim();
        String accountId = request.accountId() == null || request.accountId().isBlank()
                ? "acct-" + slug(displayName)
                : request.accountId().trim();
        String role = request.role() == null || request.role().isBlank()
                ? "viewer"
                : request.role().trim();
        RelayAccountDescriptor account = upsertAccount(accountId, displayName, caller.organizationId(), role);
        recordAudit("relay.directory.account.upserted", caller.displayName(), "account " + account.accountId() + " upserted", caller.organizationId(), null);
        saveState();
        return account;
    }

    synchronized RelayEntitlementResponse directoryEntitlement(String accountSessionToken, String organizationId) {
        RelayAccountDescriptor caller = requireDashboardAccount(accountSessionToken);
        if (organizationId != null && !organizationId.isBlank() && !organizationId.trim().equals(caller.organizationId())) {
            throw new IllegalArgumentException("Relay account is not a member of this organization");
        }
        return entitlementFor(caller.organizationId()).response(caller.organizationId());
    }

    synchronized RelayEntitlementResponse directoryUpsertEntitlement(String accountSessionToken, RelayEntitlementRequest request) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        return upsertEntitlement(caller.organizationId(), request);
    }

    synchronized RelayAccountLoginResponse rotateAccountSession(String accountSessionToken) {
        RelayAccountSessionContext existing = validateAccountSession(accountSessionToken);
        if (existing == null) {
            throw new RelayAuthException("Invalid relay account session");
        }
        RelayAccountDescriptor account = existing.account();
        // Revoke current token and mint a new one.
        Instant revokeAt = existing.tokenExpiresAt() != null
                ? existing.tokenExpiresAt()
                : Instant.parse(existing.sessionExpiresAt());
        revokeToken(existing.jti(), revokeAt);
        accountSessions.remove(existing.jti());
        Instant expiresAt = Instant.now().plus(properties.leaseTtl());
        RelayAuthTokenService.RelayAccountSessionToken issued = tokenService.issueAccountSession(
                account.accountId(),
                account.organizationId(),
                account.role(),
                expiresAt
        );
        accountSessions.put(issued.jti(), new RelayAccountSession(account.accountId(), expiresAt.toString()));
        recordAudit("relay.account.session.rotated", account.displayName(), "account session rotated", account.organizationId(), null);
        saveState();
        return new RelayAccountLoginResponse(issued.token(), expiresAt.toString(), account);
    }

    synchronized RelayHostedDashboardResponse dashboard(String accountSessionToken) {
        RelayAccountDescriptor account = requireDashboardAccount(accountSessionToken);
        RelayOrganizationDescriptor organization = requireOrganization(account.organizationId());
        RelayEntitlementResponse entitlement = entitlementFor(organization.organizationId()).response(organization.organizationId());
        RelayQuotaResponse quota = quotaFor(organization.organizationId());
        List<RelayProjectDescriptor> organizationProjects = projects.values().stream()
                .filter(project -> organization.organizationId().equals(project.organizationId()))
                .sorted(java.util.Comparator.comparing(RelayProject::projectName).thenComparing(RelayProject::projectId))
                .map(RelayProject::descriptor)
                .toList();
        List<RelayHostedSessionSummary> organizationSessions = sessions.values().stream()
                .filter(record -> organization.organizationId().equals(record.organizationId))
                .sorted(java.util.Comparator
                        .comparing((RelaySessionRecord record) -> sortTimestamp(record.lastPublishedAt, record.lastAttachedAt))
                        .reversed()
                        .thenComparing(record -> record.sessionId))
                .map(record -> new RelayHostedSessionSummary(
                        record.sessionId,
                        record.ownerName,
                        record.projectId,
                        projectName(record.projectId),
                        record.relayStatus,
                        record.tunnelStatus,
                        currentAccessScope(record),
                        viewerUrl(record.sessionId),
                        record.currentView != null && record.currentView.available(),
                        record.lastPublishedAt,
                        record.sessionVersion,
                        record.currentView == null ? 0 : record.currentView.activeMemberCount(),
                        activeViewerSessionCount(record),
                        record.currentView == null ? 0 : record.currentView.replayCount(),
                        record.currentView == null ? 0 : record.currentView.debugNoteCount(),
                        record.currentView == null ? 0 : record.currentView.recordingCount()
                ))
                .toList();
        return new RelayHostedDashboardResponse(
                account,
                organization,
                entitlement,
                quota,
                status(),
                organizationSessions,
                organizationProjects,
                accounts(organization.organizationId())
        );
    }

    synchronized List<RelayProjectDescriptor> dashboardProjects(String accountSessionToken) {
        RelayAccountDescriptor caller = requireDashboardAccount(accountSessionToken);
        return projects.values().stream()
                .filter(project -> caller.organizationId().equals(project.organizationId()))
                .sorted(java.util.Comparator.comparing(RelayProject::projectName).thenComparing(RelayProject::projectId))
                .map(RelayProject::descriptor)
                .toList();
    }

    synchronized RelayProjectDescriptor dashboardUpsertProject(String accountSessionToken, RelayProjectUpsertRequest request) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        ensureTeamEntitlement(caller.organizationId());
        if (request == null || request.projectName() == null || request.projectName().isBlank()) {
            throw new IllegalArgumentException("Missing project name");
        }
        RelayQuotaResponse quota = quotaFor(caller.organizationId());
        enforceQuota(caller.organizationId(), quota.maxProjects(), quota.projectCount(), "Organization has reached max projects");
        String now = Instant.now().toString();
        String projectId = request.projectId() == null || request.projectId().isBlank()
                ? "proj_" + UUID.randomUUID()
                : request.projectId().trim();
        RelayProject existing = projects.get(projectId);
        RelayProject project = new RelayProject(
                projectId,
                caller.organizationId(),
                request.projectName().trim(),
                existing == null ? now : existing.createdAt(),
                now
        );
        projects.put(project.projectId(), project);
        recordAudit("relay.dashboard.project.upserted", caller.displayName(), "project " + project.projectId() + " upserted", caller.organizationId(), null);
        saveState();
        return project.descriptor();
    }

    synchronized void dashboardAssignSessionProject(String accountSessionToken, String sessionId, RelaySessionProjectAssignRequest request) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        RelaySessionRecord record = requireSession(sessionId);
        if (!caller.organizationId().equals(record.organizationId)) {
            throw new IllegalArgumentException("Relay account is not a member of this session organization");
        }
        ensureTeamEntitlement(record.organizationId);
        String projectId = request == null ? null : request.projectId();
        if (projectId != null && !projectId.isBlank()) {
            RelayProject project = projects.get(projectId.trim());
            if (project == null || !caller.organizationId().equals(project.organizationId())) {
                throw new IllegalArgumentException("Unknown projectId for organization");
            }
            record.projectId = project.projectId();
        } else {
            record.projectId = null;
        }
        recordAudit("relay.dashboard.session.project.assigned", caller.displayName(), "session " + sessionId + " project=" + nullToEmpty(record.projectId), caller.organizationId(), sessionId);
        saveState();
    }

    synchronized RelayUsageAnalyticsResponse usageAnalytics(String accountSessionToken) {
        RelayAccountDescriptor account = requireDashboardAccount(accountSessionToken);
        String organizationId = account.organizationId();
        int sessionCount = 0;
        int publishedSessionCount = 0;
        int attachedSessionCount = 0;
        int activeViewerSessionCount = 0;
        int requestArtifactCount = 0;
        int replayEntryCount = 0;
        int debugNoteCount = 0;
        int recordingCount = 0;
        java.util.Map<String, Long> actorCounts = new java.util.HashMap<>();
        java.util.LinkedHashSet<String> replayTitles = new java.util.LinkedHashSet<>();

        for (RelaySessionRecord record : sessions.values()) {
            if (!organizationId.equals(record.organizationId)) {
                continue;
            }
            sessionCount += 1;
            if (record.currentView != null && record.currentView.available()) {
                publishedSessionCount += 1;
                replayEntryCount += record.currentView.replayCount();
                debugNoteCount += record.currentView.debugNoteCount();
                recordingCount += record.currentView.recordingCount();
                if (record.currentView.recentActors() != null) {
                    for (String actor : record.currentView.recentActors()) {
                        if (actor != null && !actor.isBlank()) {
                            actorCounts.merge(actor, 1L, Long::sum);
                        }
                    }
                }
                if (record.currentView.recentReplayTitles() != null) {
                    for (String title : record.currentView.recentReplayTitles()) {
                        if (title != null && !title.isBlank()) {
                            replayTitles.add(title);
                        }
                    }
                }
            }
            if (record.connectionId != null) {
                attachedSessionCount += 1;
            }
            pruneExpiredViewerSessions(record);
            activeViewerSessionCount += record.viewerSessions.size();
            requestArtifactCount += record.requestArtifacts.size();
        }

        int auditEventCount = (int) auditEvents.stream()
                .filter(event -> organizationId.equals(event.organizationId()))
                .count();

        List<String> topActors = actorCounts.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(java.util.Map.Entry::getKey))
                .limit(5)
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .toList();

        return new RelayUsageAnalyticsResponse(
                organizationId,
                sessionCount,
                publishedSessionCount,
                attachedSessionCount,
                activeViewerSessionCount,
                requestArtifactCount,
                replayEntryCount,
                debugNoteCount,
                recordingCount,
                auditEventCount,
                topActors,
                replayTitles.stream().limit(8).toList()
        );
    }

    synchronized RelayAccountDescriptor dashboardUpsertAccount(String accountSessionToken, RelayDashboardAccountRequest request) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        RelayAccountDescriptor account = upsertAccount(new RelayAccountRequest(
                request.accountId(),
                request.displayName(),
                caller.organizationId(),
                request.role()
        ));
        recordAudit("relay.dashboard.account.upserted", caller.displayName(), "account " + account.accountId() + " upserted", caller.organizationId(), null);
        saveState();
        return account;
    }

    synchronized RelayAccountDescriptor dashboardUpdateAccountRole(String accountSessionToken, String accountId, RelayAccountRoleUpdateRequest request) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        RelayAccountDescriptor existing = accounts.get(accountId);
        if (existing == null) {
            throw new NoSuchElementException("Unknown relay account " + accountId);
        }
        if (!caller.organizationId().equals(existing.organizationId())) {
            throw new IllegalArgumentException("Relay account is not a member of this organization");
        }
        String desiredRole = request == null || request.role() == null || request.role().isBlank()
                ? existing.role()
                : request.role().trim();
        if (desiredRole == null || desiredRole.isBlank()) {
            desiredRole = "viewer";
        }
        if (existing.role() != null && existing.role().equalsIgnoreCase(desiredRole)) {
            return existing;
        }
        if ("owner".equalsIgnoreCase(existing.role()) && !"owner".equalsIgnoreCase(desiredRole)) {
            ensureNotLastOwner(existing.organizationId(), existing.accountId());
            ensureNotActiveSessionOwner(existing.accountId());
        }
        RelayAccountDescriptor updated = upsertAccount(existing.accountId(), existing.displayName(), existing.organizationId(), desiredRole);
        recordAudit("relay.dashboard.account.role.updated", caller.displayName(), "account " + accountId + " role=" + desiredRole, caller.organizationId(), null);
        saveState();
        return updated;
    }

    synchronized void dashboardRemoveAccount(String accountSessionToken, String accountId) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        removeAccount(caller.organizationId(), accountId);
        recordAudit("relay.dashboard.account.removed", caller.displayName(), "account " + accountId + " removed", caller.organizationId(), null);
    }

    synchronized RelayEntitlementResponse dashboardUpdateEntitlement(String accountSessionToken, RelayEntitlementRequest request) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        return upsertEntitlement(caller.organizationId(), request);
    }

    synchronized RelayAccountDescriptor billingOwner(String accountSessionToken) {
        return requireDashboardOwner(accountSessionToken);
    }

    synchronized RelayEntitlementResponse billingUpdateEntitlement(String organizationId, RelayEntitlementRequest request) {
        return upsertEntitlement(organizationId, request);
    }

    synchronized RelayCloudRequestReplayResponse cloudRequestReplay(String accountSessionToken, String sessionId, String requestId) {
        RelayAccountDescriptor account = requireDashboardAccount(accountSessionToken);
        RelaySessionRecord record = requireSession(sessionId);
        if (!account.organizationId().equals(record.organizationId)) {
            throw new IllegalArgumentException("Relay account is not a member of this session organization");
        }
        ensureTeamEntitlement(record.organizationId);
        CapturedRequest request = requestArtifact(sessionId, requestId);
        boolean replayableBody = !request.binaryBody() && !request.bodyTruncated();
        String bodyPreview = replayableBody ? nullToEmpty(request.body()) : "[body omitted: binary or truncated]";
        String curlCommand = replayableCurlCommand(request, replayableBody);
        return new RelayCloudRequestReplayResponse(
                sessionId,
                request.requestId(),
                request.method(),
                request.path(),
                request.responseStatus(),
                request.timestamp().toString(),
                replayableBody,
                bodyPreview,
                curlCommand,
                "Run this against the local or staging app that owns the session; the relay does not execute customer traffic in v1."
        );
    }

    synchronized RelayRemoteDebugResponse remoteDebugContext(String accountSessionToken, String sessionId) {
        RelayAccountDescriptor account = requireDashboardAccount(accountSessionToken);
        RelaySessionRecord record = requireSession(sessionId);
        if (!account.organizationId().equals(record.organizationId)) {
            throw new IllegalArgumentException("Relay account is not a member of this session organization");
        }
        ensureTeamEntitlement(record.organizationId);
        HostedSessionViewDescriptor view = record.currentView;
        return new RelayRemoteDebugResponse(
                record.sessionId,
                record.organizationId,
                record.ownerName,
                record.relayStatus,
                record.tunnelStatus,
                view == null ? null : view.focusedArtifactType(),
                view == null ? null : view.focusedArtifactId(),
                record.activity.size(),
                record.debugNotes.size(),
                record.recordings.size(),
                record.requestArtifacts.size(),
                recentItems(record.activity, 8),
                recentItems(record.debugNotes, 8),
                recentItems(record.recordings, 6),
                record.requestArtifacts.values().stream()
                        .sorted(java.util.Comparator.comparing(CapturedRequest::timestamp).reversed())
                        .limit(8)
                        .map(request -> new RelayDebugRequestSummary(
                                request.requestId(),
                                request.method(),
                                request.path(),
                                request.responseStatus(),
                                request.timestamp().toString(),
                                request.binaryBody(),
                                request.bodyTruncated()
                        ))
                        .toList()
        );
    }

    synchronized RelayInvitationResponse dashboardCreateInvitation(String accountSessionToken, String sessionId, RelayInvitationRequest request) {
        RelayAccountDescriptor account = requireDashboardOwner(accountSessionToken);
        RelaySessionRecord record = requireSession(sessionId);
        if (!account.organizationId().equals(record.organizationId)) {
            throw new IllegalArgumentException("Relay account is not a member of this session organization");
        }
        return createInvitation(record, account.displayName(), request);
    }

    synchronized RelayInvitationResponse createInvitation(String sessionId, RelayInvitationRequest request) {
        RelaySessionRecord record = requireSession(sessionId);
        ensureConnection(record, request.connectionId());
        return createInvitation(record, record.ownerName, request);
    }

    private RelayInvitationResponse createInvitation(RelaySessionRecord record, String actor, RelayInvitationRequest request) {
        ensureTeamEntitlement(record.organizationId);
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Missing invitation email");
        }
        String invitationId = "invite_" + UUID.randomUUID();
        String invitationToken = "invite_token_" + UUID.randomUUID();
        String role = request.role() == null || request.role().isBlank() ? "viewer" : request.role().trim();
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? request.email().trim()
                : request.displayName().trim();
        String expiresAt = request.expiresAt() == null || request.expiresAt().isBlank()
                ? Instant.now().plus(properties.leaseTtl()).toString()
                : parseInstant(request.expiresAt()).toString();
        RelayInvitation invitation = new RelayInvitation(
                invitationId,
                invitationToken,
                record.organizationId,
                request.email().trim(),
                displayName,
                role,
                expiresAt,
                false
        );
        invitations.put(invitationToken, invitation);
        recordAudit("relay.invitation.created", actor, "invitation issued for " + request.email().trim(), record.organizationId, record.sessionId);
        saveState();
        return invitation.response();
    }

    synchronized RelayAccountLoginResponse acceptInvitation(RelayInvitationAcceptRequest request) {
        if (request.invitationToken() == null || request.invitationToken().isBlank()) {
            throw new IllegalArgumentException("Missing invitation token");
        }
        pruneExpiredInvitations();
        RelayInvitation invitation = invitations.get(request.invitationToken().trim());
        if (invitation == null || invitation.accepted()) {
            throw new IllegalArgumentException("Invalid invitation token");
        }
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? invitation.displayName()
                : request.displayName().trim();
        String accountId = request.accountId() == null || request.accountId().isBlank()
                ? "acct-" + slug(displayName)
                : request.accountId().trim();
        RelayAccountDescriptor account = upsertAccount(accountId, displayName, invitation.organizationId(), invitation.role());
        invitations.put(invitation.invitationToken(), invitation.markAccepted());
        Instant expiresAt = Instant.now().plus(properties.leaseTtl());
        RelayAuthTokenService.RelayAccountSessionToken issued = tokenService.issueAccountSession(
                account.accountId(),
                account.organizationId(),
                account.role(),
                expiresAt
        );
        accountSessions.put(issued.jti(), new RelayAccountSession(account.accountId(), expiresAt.toString()));
        recordAudit("relay.invitation.accepted", account.displayName(), "invitation accepted for " + account.accountId(), account.organizationId(), null);
        saveState();
        return new RelayAccountLoginResponse(issued.token(), expiresAt.toString(), account);
    }

    synchronized void removeAccount(String organizationId, String accountId) {
        RelayAccountDescriptor account = accounts.get(accountId);
        if (account == null) {
            throw new NoSuchElementException("Unknown relay account " + accountId);
        }
        if (organizationId != null && !organizationId.isBlank() && !organizationId.equals(account.organizationId())) {
            throw new IllegalArgumentException("Account " + accountId + " is not a member of organization " + organizationId);
        }
        if ("owner".equalsIgnoreCase(account.role())) {
            ensureNotLastOwner(account.organizationId(), accountId);
        }
        boolean activeOwner = sessions.values().stream()
                .anyMatch(record -> accountId.equals(record.ownerAccountId));
        if (activeOwner) {
            throw new IllegalArgumentException("Cannot remove an account that owns an active relay session");
        }
        sessions.values().forEach(record -> record.viewerSessions.entrySet()
                .removeIf(entry -> accountId.equals(entry.getValue().accountId())));
        accounts.remove(accountId);
        saveState();
    }

    private void ensureNotLastOwner(String organizationId, String accountId) {
        long ownerCount = accounts.values().stream()
                .filter(account -> organizationId.equals(account.organizationId()))
                .filter(account -> "owner".equalsIgnoreCase(account.role()))
                .count();
        if (ownerCount <= 1) {
            throw new IllegalArgumentException("Cannot remove or demote the last owner in this organization");
        }
    }

    private void ensureNotActiveSessionOwner(String accountId) {
        boolean activeOwner = sessions.values().stream()
                .anyMatch(record -> accountId.equals(record.ownerAccountId));
        if (activeOwner) {
            throw new IllegalArgumentException("Cannot demote an account that owns an active relay session");
        }
    }

    synchronized void directoryRemoveAccount(String accountSessionToken, String accountId) {
        RelayAccountDescriptor caller = requireDashboardOwner(accountSessionToken);
        RelayAccountDescriptor account = accounts.get(accountId);
        if (account == null) {
            throw new NoSuchElementException("Unknown relay account " + accountId);
        }
        if (!caller.organizationId().equals(account.organizationId())) {
            throw new IllegalArgumentException("Relay account is not a member of this organization");
        }
        removeAccount(caller.organizationId(), accountId);
        recordAudit("relay.directory.account.removed", caller.displayName(), "account " + accountId + " removed", caller.organizationId(), null);
        saveState();
    }

    synchronized void storeRequestArtifact(RelayRequestArtifactPayload payload) {
        RelaySessionRecord record = requireSession(payload.sessionId());
        ensureTeamEntitlement(record.organizationId);
        RelayQuotaResponse quota = quotaFor(record.organizationId);
        enforceQuota(record.organizationId, quota.maxRequestArtifactsPerSession(), record.requestArtifacts.size(), "Session has reached max stored request artifacts");
        enforceQuota(record.organizationId, quota.maxRequestArtifacts(), requestArtifactsForOrg(record.organizationId), "Organization has reached max stored request artifacts");
        if (payload.request() == null || payload.request().requestId() == null || payload.request().requestId().isBlank()) {
            throw new IllegalArgumentException("Missing relay request artifact");
        }
        record.requestArtifacts.put(payload.request().requestId(), payload.request());
        saveState();
    }

    synchronized void storeCollaboration(RelaySessionCollaborationPayload payload) {
        RelaySessionRecord record = requireSession(payload.sessionId());
        record.activity = copyLimited(payload.activity(), properties.hostedHistoryLimit());
        record.debugNotes = copyLimited(payload.debugNotes(), properties.hostedHistoryLimit());
        record.recordings = copyLimited(payload.recordings(), properties.hostedHistoryLimit());
        saveState();
    }

    synchronized CapturedRequest requestArtifact(String sessionId, String requestId) {
        RelaySessionRecord record = requireSession(sessionId);
        CapturedRequest request = record.requestArtifacts.get(requestId);
        if (request == null) {
            throw new NoSuchElementException("Unknown relay request artifact " + requestId);
        }
        return request;
    }

    synchronized List<SessionActivityEventDescriptor> collaborationActivity(String sessionId) {
        return copyList(requireSession(sessionId).activity);
    }

    synchronized List<SessionDebugNoteDescriptor> collaborationDebugNotes(String sessionId) {
        return copyList(requireSession(sessionId).debugNotes);
    }

    synchronized List<SessionRecordingDescriptor> collaborationRecordings(String sessionId) {
        return copyList(requireSession(sessionId).recordings);
    }

    synchronized List<SessionAuditEventDescriptor> sessionAudit(String sessionId, String connectionId) {
        RelaySessionRecord record = requireSession(sessionId);
        ensureConnection(record, connectionId);
        return auditEvents(record.organizationId, sessionId);
    }

    synchronized List<SessionAuditEventDescriptor> auditEvents(String organizationId, String sessionId) {
        return auditEvents.stream()
                .filter(event -> organizationId == null || organizationId.isBlank() || organizationId.equals(event.organizationId()))
                .filter(event -> sessionId == null || sessionId.isBlank() || sessionId.equals(event.sessionId()))
                .toList();
    }

    private HostedSessionViewDescriptor currentize(HostedSessionViewDescriptor view) {
        String durableState = view.durableState() == null || view.durableState().isBlank() ? "relay-backed" : view.durableState();
        return new HostedSessionViewDescriptor(
                view.sessionId(),
                view.viewId(),
                view.syncId(),
                view.available(),
                durableState,
                view.sessionVersion(),
                view.publishedVersion(),
                view.relayViewerUrl() == null || view.relayViewerUrl().isBlank() ? viewerUrl(view.sessionId()) : view.relayViewerUrl(),
                view.ownerName(),
                view.accessScope(),
                view.activeMemberCount(),
                view.replayCount(),
                view.debugNoteCount(),
                view.recordingCount(),
                view.focusedArtifactType(),
                view.focusedArtifactId(),
                view.lastPublishedAt(),
                view.members(),
                view.recentActors(),
                view.recentReplayTitles()
        );
    }

    private RelaySessionRecord requireSession(String sessionId) {
        RelaySessionRecord record = sessions.get(sessionId);
        if (record == null) {
            throw new NoSuchElementException("Unknown relay session " + sessionId);
        }
        return record;
    }

    private RelayOrganizationDescriptor requireOrganization(String organizationId) {
        RelayOrganizationDescriptor organization = organizations.get(organizationId);
        if (organization == null) {
            throw new NoSuchElementException("Unknown relay organization " + organizationId);
        }
        return organization;
    }

    private RelayEntitlement entitlementFor(String organizationId) {
        return entitlements.getOrDefault(organizationId, RelayEntitlement.alphaTrial());
    }

    private void ensureTeamEntitlement(String organizationId) {
        RelayEntitlement entitlement = entitlementFor(organizationId);
        if (!entitlement.teamEnabled()) {
            throw new PlanRequiredException("Hosted collaboration requires an active Team plan for organization " + organizationId);
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now().plus(properties.leaseTtl()) : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid access token expiry");
        }
    }

    private void pruneExpiredTokens(RelaySessionRecord record) {
        Instant now = Instant.now();
        record.accessTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void pruneExpiredViewerSessions(RelaySessionRecord record) {
        Instant now = Instant.now();
        record.viewerSessions.entrySet().removeIf(entry -> Instant.parse(entry.getValue().expiresAt()).isBefore(now));
    }

    private RelayViewerAccess viewerAccess(RelaySessionRecord record,
                                           String sessionId,
                                           RelayViewerSessionRequest request,
                                           String viewerSessionId) {
        RelayAccountSessionContext accountSession = validateAccountSession(request.accountSessionToken());
        if (accountSession != null) {
            RelayAccountDescriptor account = accountSession.account();
            if (!record.organizationId.equals(account.organizationId())) {
                throw new IllegalArgumentException("Relay account is not a member of this session organization");
            }
            String role = accountSession.role() == null || accountSession.role().isBlank()
                    ? (account.role() == null || account.role().isBlank() ? "viewer" : account.role())
                    : accountSession.role();
            return new RelayViewerAccess(account, role, accountSession.sessionExpiresAt());
        }

        RelayAccessGrant grant = validateAccessToken(sessionId, request.token());
        String viewerName = request.viewerName() == null || request.viewerName().isBlank()
                ? "shared-viewer"
                : request.viewerName().trim();
        RelayAccountDescriptor account = upsertAccount(
                "acct-viewer-" + viewerSessionId,
                viewerName,
                record.organizationId,
                grant.role()
        );
        return new RelayViewerAccess(account, grant.role(), grant.expiresAt());
    }

    private RelayAccountSessionContext validateAccountSession(String accountSessionToken) {
        if (accountSessionToken == null || accountSessionToken.isBlank()) {
            return null;
        }
        pruneExpiredAccountSessions();
        RelayAuthTokenService.RelayAccountSessionClaims claims = tokenService.verifyAccountSession(accountSessionToken);
        if (isRevoked(claims.jti())) {
            throw new RelayAuthException("Invalid relay account session");
        }
        RelayAccountSession accountSession = accountSessions.get(claims.jti());
        if (accountSession == null) {
            throw new RelayAuthException("Invalid relay account session");
        }
        if (!claims.accountId().equals(accountSession.accountId())) {
            throw new RelayAuthException("Invalid relay account session");
        }
        RelayAccountDescriptor account = accounts.get(accountSession.accountId());
        if (account == null) {
            throw new RelayAuthException("Invalid relay account session");
        }
        if (!claims.organizationId().equals(account.organizationId())) {
            throw new RelayAuthException("Invalid relay account session");
        }
        if (claims.role() != null
                && !claims.role().isBlank()
                && account.role() != null
                && !account.role().isBlank()
                && !claims.role().equalsIgnoreCase(account.role())) {
            throw new RelayAuthException("Invalid relay account session");
        }
        return new RelayAccountSessionContext(
                account,
                claims.accountId(),
                claims.organizationId(),
                claims.role(),
                accountSession.expiresAt(),
                claims.expiresAt(),
                claims.jti()
        );
    }

    private RelayAccountDescriptor requireDashboardOwner(String accountSessionToken) {
        RelayAccountDescriptor account = requireDashboardAccount(accountSessionToken);
        if (!"owner".equalsIgnoreCase(account.role())) {
            throw new IllegalStateException("Relay account session requires owner role");
        }
        return account;
    }

    private RelayAccountDescriptor requireDashboardAccount(String accountSessionToken) {
        RelayAccountSessionContext accountSession = validateAccountSession(accountSessionToken);
        if (accountSession == null) {
            throw new IllegalArgumentException("Missing relay account session");
        }
        return accountSession.account();
    }

    private void pruneExpiredAccountSessions() {
        Instant now = Instant.now();
        accountSessions.entrySet().removeIf(entry -> Instant.parse(entry.getValue().expiresAt()).isBefore(now));
    }

    private void pruneExpiredRevocations() {
        Instant now = Instant.now();
        revokedTokenJtis.entrySet().removeIf(entry -> Instant.parse(entry.getValue()).isBefore(now));
    }

    private boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        pruneExpiredRevocations();
        return revokedTokenJtis.containsKey(jti);
    }

    private void revokeToken(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        revokedTokenJtis.put(jti, expiresAt.toString());
    }

    private void pruneExpiredInvitations() {
        Instant now = Instant.now();
        invitations.entrySet().removeIf(entry -> Instant.parse(entry.getValue().expiresAt()).isBefore(now));
    }

    private void ensureConnection(RelaySessionRecord record, String connectionId) {
        if (record.connectionId == null || !record.connectionId.equals(connectionId)) {
            throw new IllegalArgumentException("Unknown connectionId for session " + record.sessionId);
        }
    }

    private String viewerUrl(String sessionId) {
        return properties.publicBaseUrl() + "/view/" + sessionId;
    }

    private String projectName(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        RelayProject project = projects.get(projectId);
        return project == null ? null : project.projectName();
    }

    private java.time.Instant sortTimestamp(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return java.time.Instant.parse(primary);
        }
        if (fallback != null && !fallback.isBlank()) {
            return java.time.Instant.parse(fallback);
        }
        return java.time.Instant.EPOCH;
    }

    private String currentAccessScope(RelaySessionRecord record) {
        if (record.currentView != null && record.currentView.accessScope() != null && !record.currentView.accessScope().isBlank()) {
            return record.currentView.accessScope();
        }
        return String.join("-", record.allowedRoles);
    }

    private int activeViewerSessionCount(RelaySessionRecord record) {
        pruneExpiredViewerSessions(record);
        return record.viewerSessions.size();
    }

    private RelayQuotaResponse quotaFor(String organizationId) {
        RelayEntitlement entitlement = entitlementFor(organizationId);
        int seatLimit = entitlement.seatLimit();
        boolean teamEnabled = entitlement.teamEnabled();

        int maxSessions = teamEnabled ? Math.max(10, seatLimit * 25) : 0;
        int maxProjects = teamEnabled ? Math.max(5, seatLimit * 5) : 0;
        int maxViewerSessions = teamEnabled ? Math.max(50, seatLimit * 100) : 0;
        int maxRequestArtifactsPerSession = teamEnabled ? 250 : 0;
        int maxRequestArtifacts = teamEnabled ? Math.max(1000, seatLimit * 2000) : 0;

        int sessionCount = sessionsForOrg(organizationId);
        int projectCount = projectsForOrg(organizationId);
        int viewerSessionCount = viewerSessionsForOrg(organizationId);
        int requestArtifactCount = requestArtifactsForOrg(organizationId);

        return new RelayQuotaResponse(
                organizationId,
                maxSessions,
                sessionCount,
                maxProjects,
                projectCount,
                maxViewerSessions,
                viewerSessionCount,
                maxRequestArtifactsPerSession,
                maxRequestArtifacts,
                requestArtifactCount
        );
    }

    private int sessionsForOrg(String organizationId) {
        int count = 0;
        for (RelaySessionRecord record : sessions.values()) {
            if (organizationId.equals(record.organizationId)) {
                count += 1;
            }
        }
        return count;
    }

    private int projectsForOrg(String organizationId) {
        int count = 0;
        for (RelayProject project : projects.values()) {
            if (organizationId.equals(project.organizationId())) {
                count += 1;
            }
        }
        return count;
    }

    private int viewerSessionsForOrg(String organizationId) {
        int count = 0;
        for (RelaySessionRecord record : sessions.values()) {
            if (!organizationId.equals(record.organizationId)) {
                continue;
            }
            pruneExpiredViewerSessions(record);
            count += record.viewerSessions.size();
        }
        return count;
    }

    private int requestArtifactsForOrg(String organizationId) {
        int count = 0;
        for (RelaySessionRecord record : sessions.values()) {
            if (organizationId.equals(record.organizationId)) {
                count += record.requestArtifacts.size();
            }
        }
        return count;
    }

    private void enforceQuota(String organizationId, int max, int current, String message) {
        if (max <= 0 || current < max) {
            return;
        }
        recordAudit("relay.quota.exceeded", "quota", message, organizationId, null);
        throw new QuotaExceededException(message);
    }

    private String replayableCurlCommand(CapturedRequest request, boolean replayableBody) {
        StringBuilder command = new StringBuilder("curl -i -X ")
                .append(shellQuote(request.method()))
                .append(" ")
                .append(shellQuote(request.path()));
        if (replayableBody && request.body() != null && !request.body().isBlank()) {
            command.append(" --data ")
                    .append(shellQuote(request.body()));
        }
        return command.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String shellQuote(String value) {
        return "'" + nullToEmpty(value).replace("'", "'\\''") + "'";
    }

    private String streamUrl(String tunnelId, String connectionId) {
        return properties.publicBaseUrl() + "/sessions/tunnel/" + tunnelId + "/stream?connectionId=" + connectionId;
    }

    private String slug(String value) {
        if (value == null || value.isBlank()) {
            return "local-developer";
        }
        String slug = value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "local-developer" : slug;
    }

    private RelayOrganizationDescriptor upsertOrganization(String ownerName) {
        String slug = slug(ownerName);
        String organizationId = "org-" + slug;
        RelayOrganizationDescriptor organization = new RelayOrganizationDescriptor(
                organizationId,
                (ownerName == null || ownerName.isBlank() ? "Local Developer" : ownerName.trim()) + " Workspace"
        );
        organizations.put(organizationId, organization);
        return organization;
    }

    private RelayAccountDescriptor upsertAccount(String accountId, String displayName, String organizationId, String role) {
        RelayAccountDescriptor account = new RelayAccountDescriptor(
                accountId,
                displayName == null || displayName.isBlank() ? "shared-viewer" : displayName.trim(),
                organizationId,
                role == null || role.isBlank() ? "viewer" : role.trim()
        );
        accounts.put(account.accountId(), account);
        return account;
    }

    private RelaySessionRecord requireTunnel(String tunnelId) {
        return sessions.values().stream()
                .filter(record -> tunnelId.equals(record.tunnelId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown relay tunnel " + tunnelId));
    }

    private void sendTunnelEvent(RelaySessionRecord record, String eventName, String payload) {
        if (record.emitter == null) {
            return;
        }
        try {
            record.emitter.send(SseEmitter.event().name(eventName).data(payload == null ? "" : payload));
        } catch (Exception exception) {
            closeEmitter(record);
        }
    }

    private void closeEmitter(RelaySessionRecord record) {
        if (record.emitter != null) {
            try {
                record.emitter.complete();
            } finally {
                record.emitter = null;
            }
        }
    }

    private void removeEmitter(RelaySessionRecord record, SseEmitter emitter) {
        if (record.emitter == emitter) {
            record.emitter = null;
        }
    }

    private <T> List<T> copyLimited(List<T> items, int limit) {
        if (items == null || items.isEmpty() || limit <= 0) {
            return List.of();
        }
        return List.copyOf(items.stream().limit(limit).toList());
    }

    private <T> List<T> copyList(List<T> items) {
        return items == null || items.isEmpty() ? List.of() : List.copyOf(items);
    }

    private <T> List<T> recentItems(List<T> items, int limit) {
        if (items == null || items.isEmpty() || limit <= 0) {
            return List.of();
        }
        int start = Math.max(0, items.size() - limit);
        return List.copyOf(items.subList(start, items.size()));
    }

    private void recordAudit(String eventType, String actor, String detail, String organizationId, String sessionId) {
        auditEvents.add(0, new SessionAuditEventDescriptor(
                "audit_" + UUID.randomUUID(),
                eventType,
                actor,
                detail,
                Instant.now().toString(),
                organizationId,
                sessionId
        ));
        while (auditEvents.size() > properties.hostedHistoryLimit()) {
            auditEvents.remove(auditEvents.size() - 1);
        }
    }

    private void loadState() {
        if (!Files.exists(persistenceFile)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(persistenceFile);
            RelayStateSnapshot state = objectMapper.readValue(bytes, RelayStateSnapshot.class);
            loadSnapshots(state.sessions(), state.organizations(), state.accounts(), state.accountSessions(), state.invitations(), state.entitlements(), state.projects(), state.revokedTokenJtis(), state.webIdentityBindings(), state.auditEvents());
        } catch (IOException stateException) {
            try {
                List<RelaySessionRecordSnapshot> snapshots = objectMapper.readValue(
                        Files.readAllBytes(persistenceFile),
                        new TypeReference<>() {
                        }
                );
                loadSnapshots(snapshots, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
            } catch (IOException listException) {
                throw new IllegalStateException("Failed to load relay persistence from " + persistenceFile, listException);
            }
        }
    }

    private void loadSnapshots(List<RelaySessionRecordSnapshot> snapshots,
                               Map<String, RelayOrganizationDescriptor> persistedOrganizations,
                               Map<String, RelayAccountDescriptor> persistedAccounts,
                               Map<String, RelayAccountSession> persistedAccountSessions,
                               Map<String, RelayInvitation> persistedInvitations,
                               Map<String, RelayEntitlement> persistedEntitlements,
                               Map<String, RelayProject> persistedProjects,
                               Map<String, String> persistedRevokedTokenJtis,
                               Map<String, RelayWebIdentityBinding> persistedWebIdentityBindings,
                               List<SessionAuditEventDescriptor> persistedAuditEvents) {
        organizations.putAll(persistedOrganizations == null ? Map.of() : persistedOrganizations);
        accounts.putAll(persistedAccounts == null ? Map.of() : persistedAccounts);
        accountSessions.putAll(persistedAccountSessions == null ? Map.of() : persistedAccountSessions);
        invitations.putAll(persistedInvitations == null ? Map.of() : persistedInvitations);
        entitlements.putAll(persistedEntitlements == null ? Map.of() : persistedEntitlements);
        projects.putAll(persistedProjects == null ? Map.of() : persistedProjects);
        revokedTokenJtis.putAll(persistedRevokedTokenJtis == null ? Map.of() : persistedRevokedTokenJtis);
        webIdentityBindings.putAll(persistedWebIdentityBindings == null ? Map.of() : persistedWebIdentityBindings);
        auditEvents.addAll(persistedAuditEvents == null ? List.of() : persistedAuditEvents);
        pruneExpiredAccountSessions();
        pruneExpiredInvitations();
        pruneExpiredRevocations();
        (snapshots == null ? List.<RelaySessionRecordSnapshot>of() : snapshots).stream()
                    .map(RelaySessionRecord::fromSnapshot)
                    .forEach(record -> {
                        sessions.put(record.sessionId, record);
                        if (record.organizationId != null) {
                            organizations.putIfAbsent(record.organizationId, new RelayOrganizationDescriptor(record.organizationId, record.organizationName));
                        }
                        if (record.ownerAccountId != null) {
                            accounts.putIfAbsent(record.ownerAccountId, new RelayAccountDescriptor(record.ownerAccountId, record.ownerName, record.organizationId, "owner"));
                        }
                        normalizeLegacyViewerSessions(record);
                        record.viewerSessions.values().forEach(session -> accounts.putIfAbsent(
                                session.accountId(),
                                new RelayAccountDescriptor(session.accountId(), session.viewerName(), record.organizationId, session.role())
                        ));
                    });
    }

    private void normalizeLegacyViewerSessions(RelaySessionRecord record) {
        record.viewerSessions.replaceAll((viewerSessionId, session) -> {
            if (session.accountId() != null && !session.accountId().isBlank()) {
                return session;
            }
            return new RelayViewerSession(
                    session.viewerSessionId(),
                    "acct-viewer-" + viewerSessionId,
                    session.role(),
                    session.expiresAt(),
                    session.viewerName(),
                    session.createdAt()
            );
        });
    }

    private void saveState() {
        try {
            Files.createDirectories(persistenceFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    persistenceFile.toFile(),
                    snapshotState()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist relay state to " + persistenceFile, exception);
        }
    }

    private RelayStateSnapshot snapshotState() {
        return new RelayStateSnapshot(
                sessions.values().stream().map(RelaySessionRecord::snapshot).toList(),
                new HashMap<>(organizations),
                new HashMap<>(accounts),
                new HashMap<>(accountSessions),
                new HashMap<>(invitations),
                new HashMap<>(entitlements),
                new HashMap<>(projects),
                new HashMap<>(revokedTokenJtis),
                new HashMap<>(webIdentityBindings),
                List.copyOf(auditEvents)
        );
    }

    private static final class RelaySessionRecord {
        private final String sessionId;
        private final Deque<HostedSessionViewDescriptor> history = new ArrayDeque<>();
        private List<String> allowedRoles = List.of();
        private String ownerName;
        private String relayUrl;
        private String shareUrl;
        private String organizationId;
        private String organizationName;
        private String ownerAccountId;
        private String encryptedToken;
        private String expiresAt;
        private String handshakeId;
        private String connectionId;
        private String leaseId;
        private String leaseExpiresAt;
        private String relayStatus = "detached";
        private String tunnelStatus = "closed";
        private String projectId;
        private String tunnelId;
        private String tunnelOpenedAt;
        private String tunnelClosedAt;
        private String lastAttachedAt;
        private String lastSyncId;
        private String lastPublishedAt;
        private long sessionVersion;
        private HostedSessionViewDescriptor currentView;
        private final Map<String, RelayAccessToken> accessTokens = new HashMap<>();
        private final Map<String, RelayViewerSession> viewerSessions = new HashMap<>();
        private final Map<String, CapturedRequest> requestArtifacts = new HashMap<>();
        private List<SessionActivityEventDescriptor> activity = List.of();
        private List<SessionDebugNoteDescriptor> debugNotes = List.of();
        private List<SessionRecordingDescriptor> recordings = List.of();
        private SseEmitter emitter;

        private RelaySessionRecord(String sessionId) {
            this.sessionId = sessionId;
        }

        static RelaySessionRecord create(String sessionId) {
            return new RelaySessionRecord(sessionId);
        }

        RelaySessionRecordSnapshot snapshot() {
            return new RelaySessionRecordSnapshot(
                    sessionId,
                    List.copyOf(history),
                    allowedRoles,
                    ownerName,
                    relayUrl,
                    shareUrl,
                    organizationId,
                    organizationName,
                    ownerAccountId,
                    encryptedToken,
                    expiresAt,
                    handshakeId,
                    connectionId,
                    leaseId,
                    leaseExpiresAt,
                    relayStatus,
                    tunnelStatus,
                    projectId,
                    tunnelId,
                    tunnelOpenedAt,
                    tunnelClosedAt,
                    lastAttachedAt,
                    lastSyncId,
                    lastPublishedAt,
                    sessionVersion,
                    currentView,
                    new HashMap<>(accessTokens),
                    new HashMap<>(viewerSessions),
                    new HashMap<>(requestArtifacts),
                    activity,
                    debugNotes,
                    recordings
            );
        }

        static RelaySessionRecord fromSnapshot(RelaySessionRecordSnapshot snapshot) {
            RelaySessionRecord record = new RelaySessionRecord(snapshot.sessionId());
            record.history.addAll(snapshot.history() == null ? List.of() : snapshot.history());
            record.allowedRoles = snapshot.allowedRoles() == null ? List.of() : List.copyOf(snapshot.allowedRoles());
            record.ownerName = snapshot.ownerName();
            record.relayUrl = snapshot.relayUrl();
            record.shareUrl = snapshot.shareUrl();
            record.organizationId = snapshot.organizationId();
            record.organizationName = snapshot.organizationName();
            record.ownerAccountId = snapshot.ownerAccountId();
            record.encryptedToken = snapshot.encryptedToken();
            record.expiresAt = snapshot.expiresAt();
            record.handshakeId = snapshot.handshakeId();
            record.connectionId = snapshot.connectionId();
            record.leaseId = snapshot.leaseId();
            record.leaseExpiresAt = snapshot.leaseExpiresAt();
            record.relayStatus = snapshot.relayStatus();
            record.tunnelStatus = snapshot.tunnelStatus();
            record.projectId = snapshot.projectId();
            record.tunnelId = snapshot.tunnelId();
            record.tunnelOpenedAt = snapshot.tunnelOpenedAt();
            record.tunnelClosedAt = snapshot.tunnelClosedAt();
            record.lastAttachedAt = snapshot.lastAttachedAt();
            record.lastSyncId = snapshot.lastSyncId();
            record.lastPublishedAt = snapshot.lastPublishedAt();
            record.sessionVersion = snapshot.sessionVersion();
            record.currentView = snapshot.currentView();
            record.accessTokens.putAll(snapshot.accessTokens() == null ? Map.of() : snapshot.accessTokens());
            record.viewerSessions.putAll(snapshot.viewerSessions() == null ? Map.of() : snapshot.viewerSessions());
            record.requestArtifacts.putAll(snapshot.requestArtifacts() == null ? Map.of() : snapshot.requestArtifacts());
            record.activity = snapshot.activity() == null ? List.of() : List.copyOf(snapshot.activity());
            record.debugNotes = snapshot.debugNotes() == null ? List.of() : List.copyOf(snapshot.debugNotes());
            record.recordings = snapshot.recordings() == null ? List.of() : List.copyOf(snapshot.recordings());
            return record;
        }
    }

    record RelayAccessGrant(String role, String expiresAt) {
    }

    private record RelayAccessToken(String role, Instant expiresAt) {
    }

    private record RelayViewerAccess(RelayAccountDescriptor account, String role, String expiresAt) {
    }

    private record RelayAccountSession(String accountId, String expiresAt) {
    }

    record RelayWebIdentityBinding(String provider,
                                  String subject,
                                  String organizationId,
                                  String accountId,
                                  String createdAt,
                                  String lastSeenAt) {

        static String key(String provider, String subject) {
            return (provider == null ? "unknown" : provider.trim()) + "|" + (subject == null ? "unknown" : subject.trim());
        }

        RelayWebIdentityBinding touch(String now) {
            return new RelayWebIdentityBinding(provider, subject, organizationId, accountId, createdAt, now);
        }
    }

    private record RelayInvitation(
            String invitationId,
            String invitationToken,
            String organizationId,
            String email,
            String displayName,
            String role,
            String expiresAt,
            boolean accepted
    ) {
        RelayInvitationResponse response() {
            return new RelayInvitationResponse(
                    invitationId,
                    invitationToken,
                    organizationId,
                    email,
                    displayName,
                    role,
                    expiresAt,
                    accepted
            );
        }

        RelayInvitation markAccepted() {
            return new RelayInvitation(
                    invitationId,
                    invitationToken,
                    organizationId,
                    email,
                    displayName,
                    role,
                    expiresAt,
                    true
            );
        }
    }

    private record RelayEntitlement(String plan, String status, int seatLimit) {
        static RelayEntitlement alphaTrial() {
            return new RelayEntitlement("alpha-trial", "active", 5);
        }

        boolean teamEnabled() {
            return ("team".equals(plan) || "alpha-trial".equals(plan)) && "active".equals(status) && seatLimit > 0;
        }

        RelayEntitlementResponse response(String organizationId) {
            return new RelayEntitlementResponse(organizationId, plan, status, seatLimit, teamEnabled());
        }
    }

    private record RelayProject(
            String projectId,
            String organizationId,
            String projectName,
            String createdAt,
            String updatedAt
    ) {
        RelayProjectDescriptor descriptor() {
            return new RelayProjectDescriptor(projectId, organizationId, projectName, createdAt, updatedAt);
        }
    }

    record RelayViewerSessionGrant(String role, String expiresAt, String viewerName, String createdAt) {
    }

    private record RelayViewerSession(
            String viewerSessionId,
            String accountId,
            String role,
            String expiresAt,
            String viewerName,
            String createdAt
    ) {
    }

    private record RelayStateSnapshot(
            List<RelaySessionRecordSnapshot> sessions,
            Map<String, RelayOrganizationDescriptor> organizations,
            Map<String, RelayAccountDescriptor> accounts,
            Map<String, RelayAccountSession> accountSessions,
            Map<String, RelayInvitation> invitations,
            Map<String, RelayEntitlement> entitlements,
            Map<String, RelayProject> projects,
            Map<String, String> revokedTokenJtis,
            Map<String, RelayWebIdentityBinding> webIdentityBindings,
            List<SessionAuditEventDescriptor> auditEvents
    ) {
    }

    private record RelaySessionRecordSnapshot(
            String sessionId,
            List<HostedSessionViewDescriptor> history,
            List<String> allowedRoles,
            String ownerName,
            String relayUrl,
            String shareUrl,
            String organizationId,
            String organizationName,
            String ownerAccountId,
            String encryptedToken,
            String expiresAt,
            String handshakeId,
            String connectionId,
            String leaseId,
            String leaseExpiresAt,
            String relayStatus,
            String tunnelStatus,
            String projectId,
            String tunnelId,
            String tunnelOpenedAt,
            String tunnelClosedAt,
            String lastAttachedAt,
            String lastSyncId,
            String lastPublishedAt,
            long sessionVersion,
            HostedSessionViewDescriptor currentView,
            Map<String, RelayAccessToken> accessTokens,
            Map<String, RelayViewerSession> viewerSessions,
            Map<String, CapturedRequest> requestArtifacts,
            List<SessionActivityEventDescriptor> activity,
            List<SessionDebugNoteDescriptor> debugNotes,
            List<SessionRecordingDescriptor> recordings
    ) {
    }
}
