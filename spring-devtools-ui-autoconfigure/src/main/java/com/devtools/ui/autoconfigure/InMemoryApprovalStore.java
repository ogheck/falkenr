package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.ApprovalRequestDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryApprovalStore {

    private final Clock clock;
    private final Map<String, ApprovalState> approvals = new ConcurrentHashMap<>();

    InMemoryApprovalStore(Clock clock) {
        this.clock = clock;
    }

    ApprovalRequestDescriptor create(String permission, String target, String reason, String actor, int ttlMinutes) {
        Instant createdAt = Instant.now(clock);
        ApprovalState state = new ApprovalState(
                "approval-" + UUID.randomUUID(),
                permission,
                blankToNull(target),
                blankToNull(reason),
                actor,
                "pending",
                createdAt,
                createdAt.plusSeconds(Math.max(1, ttlMinutes) * 60L),
                null,
                null,
                null,
                null
        );
        approvals.put(state.approvalId, state);
        return state.toDescriptor();
    }

    ApprovalRequestDescriptor approve(String approvalId, String actor) {
        ApprovalState state = requireActive(approvalId);
        if (state.requestedBy != null && state.requestedBy.equalsIgnoreCase(actor)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requester cannot approve their own request");
        }
        state.status = "approved";
        state.approvedBy = actor;
        state.approvedAt = Instant.now(clock);
        return state.toDescriptor();
    }

    void consumeIfRequired(DevToolsPermission permission,
                           String approvalId,
                           String actor,
                           DevToolsUiProperties.AccessSettings settings) {
        if (!requiresApproval(permission, settings)) {
            return;
        }
        if (approvalId == null || approvalId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Approval required for " + permission.value());
        }
        ApprovalState state = requireActive(approvalId);
        if (!"approved".equals(state.status)) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Approval is not approved: " + approvalId);
        }
        if (!permission.value().equals(state.permission)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval does not match permission: " + permission.value());
        }
        state.status = "consumed";
        state.consumedBy = actor;
        state.consumedAt = Instant.now(clock);
    }

    List<ApprovalRequestDescriptor> snapshot() {
        Instant now = Instant.now(clock);
        return approvals.values().stream()
                .peek(state -> state.expireIfNeeded(now))
                .sorted(Comparator.comparing((ApprovalState state) -> state.createdAt).reversed())
                .map(ApprovalState::toDescriptor)
                .toList();
    }

    private ApprovalState requireActive(String approvalId) {
        ApprovalState state = approvals.get(approvalId);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown approval request: " + approvalId);
        }
        state.expireIfNeeded(Instant.now(clock));
        if ("expired".equals(state.status)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Approval request expired: " + approvalId);
        }
        return state;
    }

    private boolean requiresApproval(DevToolsPermission permission, DevToolsUiProperties.AccessSettings settings) {
        if (!settings.getApproval().isEnabled()) {
            return false;
        }
        String mode = settings.getMode();
        if (mode == null || mode.isBlank() || "localhost".equalsIgnoreCase(mode)) {
            return false;
        }
        return settings.getApproval().getRequiredPermissions().contains(permission.value());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class ApprovalState {
        private final String approvalId;
        private final String permission;
        private final String target;
        private final String reason;
        private final String requestedBy;
        private String status;
        private final Instant createdAt;
        private final Instant expiresAt;
        private String approvedBy;
        private Instant approvedAt;
        private String consumedBy;
        private Instant consumedAt;

        private ApprovalState(String approvalId,
                              String permission,
                              String target,
                              String reason,
                              String requestedBy,
                              String status,
                              Instant createdAt,
                              Instant expiresAt,
                              String approvedBy,
                              Instant approvedAt,
                              String consumedBy,
                              Instant consumedAt) {
            this.approvalId = approvalId;
            this.permission = permission;
            this.target = target;
            this.reason = reason;
            this.requestedBy = requestedBy;
            this.status = status;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.approvedBy = approvedBy;
            this.approvedAt = approvedAt;
            this.consumedBy = consumedBy;
            this.consumedAt = consumedAt;
        }

        private void expireIfNeeded(Instant now) {
            if (expiresAt.isBefore(now) && "pending".equals(status)) {
                status = "expired";
            }
        }

        private ApprovalRequestDescriptor toDescriptor() {
            return new ApprovalRequestDescriptor(
                    approvalId,
                    permission,
                    target,
                    reason,
                    requestedBy,
                    status,
                    createdAt.toString(),
                    expiresAt.toString(),
                    approvedBy,
                    approvedAt == null ? null : approvedAt.toString(),
                    consumedBy,
                    consumedAt == null ? null : consumedAt.toString()
            );
        }
    }
}
