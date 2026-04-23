import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { PagedResponse, TimeTravelStateDescriptor } from "./types";

const endpointPage = {
  items: [{ method: "GET", path: "/hello", controller: "HelloController", methodName: "hello" }],
  total: 1,
  offset: 0,
  limit: 25,
};

const requestPage = {
  items: [{
    requestId: "req_echo_1",
    method: "POST",
    path: "/echo",
    headers: { "X-Test": ["yes"] },
    body: "{\"name\":\"devtools\"}",
    bodyTruncated: false,
    binaryBody: false,
    timestamp: "2026-04-07T20:00:00Z",
    responseStatus: 200,
  }],
  total: 40,
  offset: 0,
  limit: 25,
};

const configPage = {
  items: [{ key: "test.message", value: "hello", propertySource: "application.yml" }],
  total: 1,
  offset: 0,
  limit: 25,
};

const configSnapshotsPage = {
  items: [{
    snapshotId: "cfg_1",
    label: "Baseline",
    capturedAt: "2026-04-08T12:00:00Z",
    properties: [{ key: "test.message", value: "hello", propertySource: "application.yml" }],
  }],
  total: 1,
  offset: 0,
  limit: 10,
};

const configComparison = {
  snapshot: configSnapshotsPage.items[0],
  addedCount: 1,
  removedCount: 0,
  changedCount: 1,
  unchangedCount: 0,
  entries: [
    {
      key: "feature.alpha",
      status: "ADDED",
      currentValue: "true",
      currentPropertySource: "application.yml",
      snapshotValue: "",
      snapshotPropertySource: "",
    },
    {
      key: "test.message",
      status: "CHANGED",
      currentValue: "hello-now",
      currentPropertySource: "application.yml",
      snapshotValue: "hello",
      snapshotPropertySource: "application.yml",
    },
  ],
};

const configDrift = {
  available: true,
  comparedAt: "2026-04-08T12:06:00Z",
  snapshot: configSnapshotsPage.items[0],
  drifted: true,
  totalChanges: 2,
  addedCount: 1,
  removedCount: 0,
  changedCount: 1,
  unchangedCount: 0,
  entries: configComparison.entries,
};

const featureFlagPage = {
  items: [{ key: "features.checkout", enabled: false, propertySource: "application.yml", overridden: false }],
  total: 1,
  offset: 0,
  limit: 25,
};

const dependencyPage = {
  items: [{
    beanName: "testController",
    beanType: "TestController",
    scope: "singleton",
    dependencies: ["jdbcProbe", "testLogProbe"],
    dependents: [],
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const logPage = {
  items: [{
    timestamp: "2026-04-07T20:00:00Z",
    level: "INFO",
    message: "Booted",
    logger: "com.devtools.Test",
    stackTrace: null,
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const jobPage = {
  items: [{
    beanName: "scheduledProbe",
    beanType: "ScheduledProbe",
    methodName: "heartbeat",
    triggerType: "fixedDelay",
    expression: "30000ms",
    scheduler: "",
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const dbQueryPage = {
  items: [{
    timestamp: "2026-04-07T20:00:00Z",
    sql: "select id, name from users order by id",
    statementType: "select",
    dataSource: "dataSource",
    rowsAffected: null,
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const webhookTargetPage = {
  items: [{
    method: "POST",
    path: "/webhooks/github",
    controller: "TestController",
    methodName: "githubWebhook",
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const fakeServicesPage = {
  items: [{
    serviceId: "github",
    displayName: "GitHub Webhooks",
    description: "Responds like a simple webhook receiver and delivery probe target.",
    basePath: "/_dev/fake/github",
    enabled: false,
    routes: ["POST /_dev/fake/github/webhooks", "GET /_dev/fake/github/status"],
    mockResponses: [
      {
        routeId: "github:POST /_dev/fake/github/webhooks",
        route: "POST /_dev/fake/github/webhooks",
        status: 200,
        contentType: "application/json",
        body: "",
      },
      {
        routeId: "github:GET /_dev/fake/github/status",
        route: "GET /_dev/fake/github/status",
        status: 200,
        contentType: "application/json",
        body: "",
      },
    ],
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const auditLogsPage = {
  items: [{
    auditId: "auditlog-1",
    category: "feature-flags",
    action: "override.set",
    actor: "local-operator",
    detail: "features.checkout=true",
    timestamp: "2026-04-08T12:10:00Z",
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const timePage: PagedResponse<TimeTravelStateDescriptor> = {
  items: [{
    currentTime: "2026-04-08T12:00:00Z",
    zoneId: "UTC",
    overridden: false,
    overrideReason: null,
    overriddenBy: null,
    overriddenAt: null,
    expiresAt: null,
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const sessionPage = {
  items: [{
    sessionId: "session-123",
    agentStatus: "ready",
    relayStatus: "idle",
    attached: false,
    relayUrl: "wss://relay.spring-devtools-ui.dev/sessions/session-123",
    shareUrl: "https://app.spring-devtools-ui.dev/s/session-123",
    activity: [] as Array<{
      eventType: string;
      actor: string;
      detail: string;
      timestamp: string;
    }>,
    replay: [] as Array<{
      replayId: string;
      category: string;
      title: string;
      payloadPreview: string;
      artifactType: string | null;
      artifactId: string | null;
      occurredAt: string;
    }>,
    debugNotes: [] as Array<{
      noteId: string;
      author: string;
      message: string;
      artifactType: string | null;
      artifactId: string | null;
      createdAt: string;
    }>,
    audit: [] as Array<{
      auditId: string;
      eventType: string;
      actor: string;
      detail: string;
      recordedAt: string;
    }>,
    recordings: [] as Array<{
      recordingId: string;
      startedBy: string;
      startedAt: string;
      stoppedAt: string | null;
      active: boolean;
      activityCount: number;
      replayCount: number;
      debugNoteCount: number;
      activeMemberCount: number;
      focusedArtifactType: string | null;
      focusedArtifactId: string | null;
      highlights: string[];
    }>,
    accessScope: "owner-viewer",
    allowedRoles: ["owner", "viewer"],
    activeMembers: [] as Array<{
      memberId: string;
      role: string;
      source: string;
      joinedAt: string;
      lastSeenAt: string;
    }>,
    workspaceMembers: [] as Array<{
      memberId: string;
      role: string;
      source: string;
      joinedAt: string;
      lastSeenAt: string;
      focusedArtifactType: string | null;
      focusedArtifactId: string | null;
      lastAction: string;
    }>,
    ownerCount: 0,
    viewerMemberCount: 0,
    guestMemberCount: 0,
    recentActors: [] as string[],
    viewerCount: 0,
    activeShareTokens: 0,
    ownerName: "local-developer",
    relayOrganizationId: null as string | null,
    relayOrganizationName: null as string | null,
    relayOwnerAccountId: null as string | null,
    ownerTokenPreview: "abcd1234…9f0e",
    lastIssuedShareRole: null as string | null,
    lastIssuedShareTokenPreview: "[unavailable]",
    tokenMode: "aes-gcm",
    relayMode: "local-stub",
    relayHandshakeId: null as string | null,
    relayConnectionId: null as string | null,
    relayLeaseId: null as string | null,
    relayLeaseExpiresAt: null as string | null,
    relayViewerUrl: null as string | null,
    relayTunnelId: null as string | null,
    tunnelStatus: "idle",
    tunnelOpenedAt: null as string | null,
    tunnelClosedAt: null as string | null,
    lastError: null as string | null,
    focusedArtifactType: null as string | null,
    focusedArtifactId: null as string | null,
    focusedBy: null as string | null,
    focusedAt: null as string | null,
    recordingActive: false,
    currentRecordingId: null as string | null,
    recordingStartedAt: null as string | null,
    recordingStoppedAt: null as string | null,
    syncStatus: "idle",
    lastSyncId: null as string | null,
    lastSyncedAt: null as string | null,
    sessionVersion: 0,
    publishedSessionVersion: null as number | null,
    hostedViewStatus: "unpublished",
    lastPublishedAt: null as string | null,
    activityRetentionLimit: 25,
    replayRetentionLimit: 50,
    recordingRetentionLimit: 10,
    auditRetentionLimit: 50,
    lastHeartbeatAt: null as string | null,
    nextHeartbeatAt: null as string | null,
    reconnectAt: null as string | null,
    lastRotatedAt: "2026-04-08T12:00:00Z",
    expiresAt: "2026-04-08T20:00:00Z",
  }],
  total: 1,
  offset: 0,
  limit: 25,
};

const hostedSessionView = {
  sessionId: "session-123",
  viewId: "view-session-123",
  syncId: null as string | null,
  available: false,
  durableState: "unpublished",
  sessionVersion: 0,
  publishedVersion: null as number | null,
  relayViewerUrl: "https://relay.spring-devtools-ui.dev/view/session-123",
  ownerName: "local-developer",
  accessScope: "owner-viewer",
  activeMemberCount: 0,
  replayCount: 0,
  debugNoteCount: 0,
  recordingCount: 0,
  focusedArtifactType: null as string | null,
  focusedArtifactId: null as string | null,
  lastPublishedAt: null as string | null,
  members: [] as Array<{
    memberId: string;
    role: string;
    source: string;
    joinedAt: string | null;
    publishedAt: string | null;
    focusedArtifactType: string | null;
    focusedArtifactId: string | null;
    lastAction: string;
  }>,
  recentActors: [] as string[],
  recentReplayTitles: [] as string[],
};

const relayViewerSessionsPage = {
  items: [] as Array<{
    viewerSessionId: string;
    role: string;
    viewerName: string;
    createdAt: string;
    expiresAt: string;
  }>,
  total: 0,
  offset: 0,
  limit: 10,
};

const relaySessionIdentity = {
  sessionId: "session-123",
  organization: {
    organizationId: "org-local-developer",
    organizationName: "local-developer Workspace",
  },
  owner: {
    accountId: "acct-local-developer",
    displayName: "local-developer",
    organizationId: "org-local-developer",
    role: "owner",
  },
  collaborators: [] as Array<{
    accountId: string;
    displayName: string;
    organizationId: string;
    role: string;
  }>,
};

describe("App", () => {
  beforeEach(() => {
    let currentFeatureFlag = { ...featureFlagPage.items[0] };
    let currentConfigSnapshots = [...configSnapshotsPage.items];
    let currentFakeService = { ...fakeServicesPage.items[0] };
    let currentTimeState = { ...timePage.items[0] };
    let currentSession = { ...sessionPage.items[0] };
    let currentHostedView = { ...hostedSessionView };
    let currentHostedHistory = [] as typeof hostedSessionView[];
    let currentRelayViewerSessions = [] as typeof relayViewerSessionsPage.items;
    let currentRelaySessionIdentity = { ...relaySessionIdentity };

    vi.stubGlobal("fetch", vi.fn((input: string | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (url.includes("/endpoints")) {
        return Promise.resolve(jsonResponse(endpointPage));
      }
      if (url.includes("/requests")) {
        const requestUrl = new URL(url, "http://localhost");
        const offset = Number(requestUrl.searchParams.get("offset") ?? "0");
        return Promise.resolve(jsonResponse({ ...requestPage, offset }));
      }
      if (url.includes("/requests/replay")) {
        return Promise.resolve(jsonResponse({
          requestId: requestPage.items[0].requestId,
          method: requestPage.items[0].method,
          path: requestPage.items[0].path,
          originalStatus: requestPage.items[0].responseStatus,
          replayStatus: 500,
          replayedAt: "2026-04-08T12:20:00Z",
          responseBody: "{\"error\":\"boom-response\"}",
        }));
      }
      if (url.includes("/config/compare")) {
        return Promise.resolve(jsonResponse({
          ...configComparison,
          snapshot: currentConfigSnapshots[0],
        }));
      }
      if (url.includes("/config/drift")) {
        return Promise.resolve(jsonResponse({
          ...configDrift,
          snapshot: currentConfigSnapshots[0],
        }));
      }
      if (url.includes("/config/snapshots") && method === "POST") {
        const body = JSON.parse(String(init?.body ?? "{}"));
        const nextSnapshot = {
          snapshotId: "cfg_2",
          label: body.label,
          capturedAt: "2026-04-08T12:05:00Z",
          properties: configPage.items,
        };
        currentConfigSnapshots = [nextSnapshot, ...currentConfigSnapshots];
        return Promise.resolve(jsonResponse(nextSnapshot));
      }
      if (url.includes("/config/snapshots")) {
        return Promise.resolve(jsonResponse({
          items: currentConfigSnapshots,
          total: currentConfigSnapshots.length,
          offset: 0,
          limit: 10,
        }));
      }
      if (url.includes("/config")) {
        return Promise.resolve(jsonResponse(configPage));
      }
      if (url.includes("/feature-flags") && method === "POST") {
        currentFeatureFlag = {
          ...currentFeatureFlag,
          enabled: true,
          overridden: true,
          propertySource: "spring-devtools-ui-overrides",
        };
        return Promise.resolve(jsonResponse(currentFeatureFlag));
      }
      if (url.includes("/feature-flags") && method === "DELETE" && url.includes("key=")) {
        currentFeatureFlag = { ...currentFeatureFlag, enabled: false, overridden: false, propertySource: "application.yml" };
        return Promise.resolve(noContentResponse());
      }
      if (url.includes("/feature-flags")) {
        return Promise.resolve(jsonResponse({ ...featureFlagPage, items: [currentFeatureFlag] }));
      }
      if (url.includes("/audit-logs")) {
        return Promise.resolve(jsonResponse(auditLogsPage));
      }
      if (url.includes("/dependencies")) {
        return Promise.resolve(jsonResponse(dependencyPage));
      }
      if (url.includes("/time") && method === "POST") {
        currentTimeState = {
          currentTime: "2026-04-08T16:30:00Z",
          zoneId: "UTC",
          overridden: true,
          overrideReason: "Staging verification",
          overriddenBy: "admin",
          overriddenAt: "2026-04-08T12:10:00Z",
          expiresAt: "2026-04-08T12:40:00Z",
        };
        return Promise.resolve(jsonResponse(currentTimeState));
      }
      if (url.includes("/time") && method === "DELETE") {
        currentTimeState = { ...timePage.items[0] };
        return Promise.resolve(noContentResponse());
      }
      if (url.includes("/time")) {
        return Promise.resolve(jsonResponse({ ...timePage, items: [currentTimeState] }));
      }
      if (url.includes("/session/attach") && method === "POST") {
        currentSession = {
          ...currentSession,
          attached: true,
          activity: [{
            eventType: "session.attached",
            actor: "pair-debugger",
            detail: "guest access enabled",
            timestamp: "2026-04-08T12:05:00Z",
          }],
          audit: [{
            auditId: "audit-1",
            eventType: "session.attached",
            actor: "pair-debugger",
            detail: "guest access enabled",
            recordedAt: "2026-04-08T12:05:00Z",
          }],
          replay: [{
            replayId: "replay-1",
            category: "session",
            title: "Session attached",
            payloadPreview: "pair-debugger attached local session",
            artifactType: null,
            artifactId: null,
            occurredAt: "2026-04-08T12:05:00Z",
          }],
          relayStatus: "connected",
          tunnelStatus: "healthy",
          accessScope: "owner-viewer-guest",
          allowedRoles: ["owner", "viewer", "guest"],
          ownerCount: 1,
          viewerMemberCount: 0,
          guestMemberCount: 0,
          workspaceMembers: [{
            memberId: "owner",
            role: "owner",
            source: "local-session",
            joinedAt: "2026-04-08T12:05:00Z",
            lastSeenAt: "2026-04-08T12:05:00Z",
            focusedArtifactType: null,
            focusedArtifactId: null,
            lastAction: "session.attached",
          }],
          recentActors: ["pair-debugger"],
          ownerName: "pair-debugger",
          relayOrganizationId: "org-pair-debugger",
          relayOrganizationName: "pair-debugger Workspace",
          relayOwnerAccountId: "acct-pair-debugger",
          relayMode: "local-stub",
          relayHandshakeId: "hs-123",
          relayConnectionId: "conn-123",
          relayLeaseId: "lease-123",
          relayLeaseExpiresAt: "2026-04-08T12:10:00Z",
          relayViewerUrl: "https://relay.spring-devtools-ui.dev/view/session-123",
          relayTunnelId: null,
          tunnelOpenedAt: null,
          tunnelClosedAt: null,
          lastHeartbeatAt: "2026-04-08T12:05:00Z",
          nextHeartbeatAt: "2026-04-08T12:05:30Z",
          reconnectAt: null,
          focusedArtifactType: null,
          focusedArtifactId: null,
          focusedBy: null,
          focusedAt: null,
          sessionVersion: 1,
          publishedSessionVersion: null,
          hostedViewStatus: "unpublished",
          lastPublishedAt: null,
        };
        currentHostedView = {
          ...currentHostedView,
          sessionVersion: 1,
          ownerName: "pair-debugger",
          accessScope: "owner-viewer-guest",
          members: [{
            memberId: "owner",
            role: "owner",
            source: "local-session",
            joinedAt: "2026-04-08T12:05:00Z",
            publishedAt: null,
            focusedArtifactType: null,
            focusedArtifactId: null,
            lastAction: "session.attached",
          }],
        };
        currentHostedHistory = [];
        currentRelayViewerSessions = [{
          viewerSessionId: "viewer-1",
          role: "viewer",
          viewerName: "qa-viewer",
          createdAt: "2026-04-08T12:06:00Z",
          expiresAt: "2026-04-08T13:06:00Z",
        }];
        currentRelaySessionIdentity = {
          sessionId: "session-123",
          organization: {
            organizationId: "org-pair-debugger",
            organizationName: "pair-debugger Workspace",
          },
          owner: {
            accountId: "acct-pair-debugger",
            displayName: "pair-debugger",
            organizationId: "org-pair-debugger",
            role: "owner",
          },
          collaborators: [{
            accountId: "acct-viewer-1",
            displayName: "qa-viewer",
            organizationId: "org-pair-debugger",
            role: "viewer",
          }],
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/owner-transfer") && method === "POST") {
        currentSession = {
          ...currentSession,
          ownerName: "qa-viewer",
          relayOwnerAccountId: "acct-viewer-1",
          sessionVersion: currentSession.sessionVersion + 1,
        };
        currentRelaySessionIdentity = {
          sessionId: "session-123",
          organization: {
            organizationId: currentSession.relayOrganizationId ?? "org-pair-debugger",
            organizationName: currentSession.relayOrganizationName ?? "pair-debugger Workspace",
          },
          owner: {
            accountId: "acct-viewer-1",
            displayName: "qa-viewer",
            organizationId: currentSession.relayOrganizationId ?? "org-pair-debugger",
            role: "owner",
          },
          collaborators: [],
        };
        return Promise.resolve(jsonResponse(currentRelaySessionIdentity));
      }
      if (url.includes("/session/tunnel/open") && method === "POST") {
        currentSession = {
          ...currentSession,
          relayTunnelId: "tunnel-123",
          tunnelStatus: "open",
          tunnelOpenedAt: "2026-04-08T12:06:00Z",
          tunnelClosedAt: null,
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/tunnel/close") && method === "POST") {
        currentSession = {
          ...currentSession,
          relayTunnelId: null,
          tunnelStatus: "closed",
          tunnelClosedAt: "2026-04-08T12:09:00Z",
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/inspect") && method === "POST") {
        currentSession = {
          ...currentSession,
          focusedArtifactType: "request",
          focusedArtifactId: "req_echo_1",
          focusedBy: "pair-debugger",
          focusedAt: "2026-04-08T12:32:00Z",
          workspaceMembers: currentSession.workspaceMembers.map((member) => member.memberId === "owner"
            ? {
              ...member,
              focusedArtifactType: "request",
              focusedArtifactId: "req_echo_1",
              lastAction: "focused request:req_echo_1",
              lastSeenAt: "2026-04-08T12:32:00Z",
            }
            : member),
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/notes") && method === "POST") {
        currentSession = {
          ...currentSession,
          debugNotes: [{
            noteId: "note-1",
            author: "pair-debugger",
            message: "Investigate response mismatch before retrying",
            artifactType: "request",
            artifactId: "req_echo_1",
            createdAt: "2026-04-08T12:33:00Z",
          }, ...currentSession.debugNotes],
          audit: [{
            auditId: "audit-note-1",
            eventType: "session.note_added",
            actor: "pair-debugger",
            detail: "Investigate response mismatch before retrying",
            recordedAt: "2026-04-08T12:33:00Z",
          }, ...currentSession.audit],
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/recording/start") && method === "POST") {
        currentSession = {
          ...currentSession,
          recordingActive: true,
          currentRecordingId: "recording-1",
          recordingStartedAt: "2026-04-08T12:34:00Z",
          recordingStoppedAt: null,
          recordings: [{
            recordingId: "recording-1",
            startedBy: "pair-debugger",
            startedAt: "2026-04-08T12:34:00Z",
            stoppedAt: null,
            active: true,
            activityCount: 2,
            replayCount: 2,
            debugNoteCount: 1,
            activeMemberCount: 1,
            focusedArtifactType: "request",
            focusedArtifactId: "req_echo_1",
            highlights: ["session.attached", "POST /echo", "focus:request:req_echo_1"],
          }],
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/recording/stop") && method === "POST") {
        currentSession = {
          ...currentSession,
          recordingActive: false,
          recordingStoppedAt: "2026-04-08T12:36:00Z",
          recordings: currentSession.recordings.map((recording) => ({
            ...recording,
            active: false,
            stoppedAt: "2026-04-08T12:36:00Z",
          })),
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/share") && method === "POST") {
        currentSession = {
          ...currentSession,
          activeShareTokens: 1,
          lastIssuedShareRole: "guest",
          lastIssuedShareTokenPreview: "share_1…9abc",
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse({
          role: "guest",
          tokenPreview: "share_1…9abc",
          shareUrl: "https://app.spring-devtools-ui.dev/s/session-123?token=share_123456789abc",
          expiresAt: "2026-04-08T20:30:00Z",
          active: true,
        }));
      }
      if (url.includes("/session/validate") && method === "POST") {
        currentSession = {
          ...currentSession,
          activity: [{
            eventType: "session.validation_succeeded",
            actor: "guest",
            detail: "access granted",
            timestamp: "2026-04-08T12:31:00Z",
          }, ...currentSession.activity],
          replay: [{
            replayId: "replay-2",
            category: "request",
            title: "POST /echo",
            payloadPreview: "status 200",
            artifactType: "request",
            artifactId: "req_echo_1",
            occurredAt: "2026-04-08T12:31:30Z",
          }, {
            replayId: "replay-3",
            category: "access",
            title: "Validation succeeded",
            payloadPreview: "guest joined",
            artifactType: null,
            artifactId: null,
            occurredAt: "2026-04-08T12:31:00Z",
          }, ...currentSession.replay],
          activeMembers: [{
            memberId: "member-1",
            role: "guest",
            source: "share-token",
            joinedAt: "2026-04-08T12:31:00Z",
            lastSeenAt: "2026-04-08T12:31:00Z",
          }],
          workspaceMembers: [{
            memberId: "owner",
            role: "owner",
            source: "local-session",
            joinedAt: "2026-04-08T12:05:00Z",
            lastSeenAt: "2026-04-08T12:05:00Z",
            focusedArtifactType: null,
            focusedArtifactId: null,
            lastAction: "session.attached",
          }, {
            memberId: "member-1",
            role: "guest",
            source: "share-token",
            joinedAt: "2026-04-08T12:31:00Z",
            lastSeenAt: "2026-04-08T12:31:00Z",
            focusedArtifactType: null,
            focusedArtifactId: null,
            lastAction: "validated access",
          }],
          viewerMemberCount: 0,
          guestMemberCount: 1,
          recentActors: ["guest", "pair-debugger"],
          viewerCount: 1,
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse({
          allowed: true,
          role: "guest",
          reason: "Access granted",
          viewerCount: 1,
          sessionId: "session-123",
        }));
      }
      if (url.includes("/session/artifacts/request")) {
        return Promise.resolve(jsonResponse(requestPage.items[0]));
      }
      if (url.includes("/session/heartbeat") && method === "POST") {
        currentSession = {
          ...currentSession,
          relayStatus: "connected",
          tunnelStatus: "healthy",
          relayTunnelId: currentSession.relayTunnelId,
          tunnelOpenedAt: currentSession.tunnelOpenedAt,
          tunnelClosedAt: null,
          lastError: null,
          lastHeartbeatAt: "2026-04-08T12:10:00Z",
          relayLeaseId: "lease-123",
          relayLeaseExpiresAt: "2026-04-08T12:15:00Z",
          nextHeartbeatAt: "2026-04-08T12:10:30Z",
          reconnectAt: null,
          sessionVersion: currentSession.sessionVersion + 1,
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/sync") && method === "POST") {
        currentSession = {
          ...currentSession,
          syncStatus: "synced",
          lastSyncId: "sync-123",
          lastSyncedAt: "2026-04-08T12:11:00Z",
          publishedSessionVersion: currentSession.sessionVersion,
          hostedViewStatus: "published",
          lastPublishedAt: "2026-04-08T12:11:00Z",
        };
        currentHostedView = {
          ...currentHostedView,
          syncId: "sync-123",
          available: true,
          durableState: "published",
          sessionVersion: currentSession.sessionVersion,
          publishedVersion: currentSession.sessionVersion,
          ownerName: currentSession.ownerName,
          accessScope: currentSession.accessScope,
          activeMemberCount: currentSession.activeMembers.length,
          replayCount: currentSession.replay.length,
          debugNoteCount: currentSession.debugNotes.length,
          recordingCount: currentSession.recordings.length,
          focusedArtifactType: currentSession.focusedArtifactType,
          focusedArtifactId: currentSession.focusedArtifactId,
          lastPublishedAt: "2026-04-08T12:11:00Z",
          members: [{
            memberId: "owner",
            role: "owner",
            source: "local-session",
            joinedAt: "2026-04-08T12:05:00Z",
            publishedAt: "2026-04-08T12:11:00Z",
            focusedArtifactType: currentSession.focusedArtifactType,
            focusedArtifactId: currentSession.focusedArtifactId,
            lastAction: "session.attached",
          }, {
            memberId: "member-1",
            role: "guest",
            source: "share-token",
            joinedAt: "2026-04-08T12:31:00Z",
            publishedAt: "2026-04-08T12:11:00Z",
            focusedArtifactType: currentSession.focusedArtifactType,
            focusedArtifactId: currentSession.focusedArtifactId,
            lastAction: "validated access",
          }],
          recentActors: currentSession.recentActors,
          recentReplayTitles: currentSession.replay.map((entry) => entry.title).slice(0, 5),
        };
        currentHostedHistory = [currentHostedView];
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session/hosted-view/members") && method === "POST") {
        currentHostedView = {
          ...currentHostedView,
          activeMemberCount: currentHostedView.members.length,
          members: [...currentHostedView.members, {
            memberId: "viewer-1",
            role: "viewer",
            source: "relay-viewer",
            joinedAt: "2026-04-08T12:12:00Z",
            publishedAt: "2026-04-08T12:11:00Z",
            focusedArtifactType: currentSession.focusedArtifactType,
            focusedArtifactId: currentSession.focusedArtifactId,
            lastAction: "hosted viewer joined",
          }],
        };
        return Promise.resolve(jsonResponse(currentHostedView));
      }
      if (url.includes("/session/hosted-view/members") && method === "DELETE") {
        currentHostedView = {
          ...currentHostedView,
          activeMemberCount: Math.max(0, currentHostedView.members.length - 1),
          members: currentHostedView.members.filter((member) => member.memberId !== "viewer-1"),
        };
        return Promise.resolve(jsonResponse(currentHostedView));
      }
      if (url.includes("/session/token") && method === "POST") {
        currentSession = {
          ...currentSession,
          ownerTokenPreview: "rotated99…1abc",
          lastRotatedAt: "2026-04-08T12:30:00Z",
          expiresAt: "2026-04-08T20:30:00Z",
        };
        return Promise.resolve(jsonResponse(currentSession));
      }
      if (url.includes("/session") && method === "DELETE") {
        currentSession = { ...sessionPage.items[0] };
        currentHostedView = { ...hostedSessionView };
        currentHostedHistory = [];
        currentRelayViewerSessions = [];
        currentRelaySessionIdentity = { ...relaySessionIdentity };
        return Promise.resolve(noContentResponse());
      }
      if (url.includes("/session/viewer-sessions") && method === "DELETE") {
        const requestUrl = new URL(url, "http://localhost");
        const viewerSessionId = requestUrl.searchParams.get("viewerSessionId");
        currentRelayViewerSessions = currentRelayViewerSessions.filter((session) => session.viewerSessionId !== viewerSessionId);
        return Promise.resolve(jsonResponse({
          items: currentRelayViewerSessions,
          total: currentRelayViewerSessions.length,
          offset: 0,
          limit: 10,
        }));
      }
      if (url.includes("/session/viewer-sessions")) {
        return Promise.resolve(jsonResponse({
          items: currentRelayViewerSessions,
          total: currentRelayViewerSessions.length,
          offset: 0,
          limit: 10,
        }));
      }
      if (url.includes("/session/identity")) {
        return Promise.resolve(jsonResponse(currentRelaySessionIdentity));
      }
      if (url.includes("/session/hosted-view")) {
        return Promise.resolve(jsonResponse(currentHostedView));
      }
      if (url.includes("/session/hosted-history")) {
        return Promise.resolve(jsonResponse({ items: currentHostedHistory, total: currentHostedHistory.length, offset: 0, limit: 5 }));
      }
      if (url.includes("/session")) {
        return Promise.resolve(jsonResponse({ ...sessionPage, items: [currentSession] }));
      }
      if (url.includes("/logs")) {
        return Promise.resolve(jsonResponse(logPage));
      }
      if (url.includes("/jobs")) {
        return Promise.resolve(jsonResponse(jobPage));
      }
      if (url.includes("/db-queries")) {
        return Promise.resolve(jsonResponse(dbQueryPage));
      }
      if (url.includes("/webhooks/targets")) {
        return Promise.resolve(jsonResponse(webhookTargetPage));
      }
      if (url.includes("/fake-services") && method === "POST") {
        const body = init && "body" in init && typeof init.body === "string" ? JSON.parse(init.body) : {};
        currentFakeService = {
          ...currentFakeService,
          enabled: body.enabled ?? true,
          mockResponses: body.mockResponse
            ? currentFakeService.mockResponses.map((mock) => mock.routeId === body.mockResponse.routeId
              ? {
                ...mock,
                status: body.mockResponse.status,
                contentType: body.mockResponse.contentType,
                body: body.mockResponse.body,
              }
              : mock)
            : currentFakeService.mockResponses,
        };
        return Promise.resolve(jsonResponse(currentFakeService));
      }
      if (url.includes("/fake-services")) {
        return Promise.resolve(jsonResponse({ ...fakeServicesPage, items: [currentFakeService] }));
      }
      if (url.includes("/api-testing/send")) {
        return Promise.resolve(jsonResponse({
          method: "POST",
          path: "/webhooks/github",
          status: 200,
          responseBody: "{\"accepted\":true}",
        }));
      }
      return Promise.reject(new Error(`Unhandled fetch URL: ${url}`));
    }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("loads the dashboard and renders server data", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByText("Observe your Spring Boot app where it runs.")).toBeInTheDocument();
    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    expect(screen.getByText("1 total")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /Requests/ })[0]);
    expect(await screen.findByText("req_echo_1")).toBeInTheDocument();
  });

  it("starts and stops a dev session recording from the session tab", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getAllByRole("button", { name: /Session/ })[0]);
    expect(await screen.findByText("Dev session recording")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Attach local session|Refresh attach session/ }));
    // The recording toggle is disabled until the attach round-trip completes.
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Start recording" })).not.toHaveAttribute("disabled");
    }, { timeout: 15000 });
    await user.click(screen.getByRole("button", { name: "Start recording" }));

    // CI can be a bit slower to repaint after the session recording start response, so anchor on the
    // stateful control first and then wait for the recording id to render.
    expect(await screen.findByRole("button", { name: "Stop recording" }, { timeout: 15000 })).toBeInTheDocument();
    expect((await screen.findAllByText("recording-1", {}, { timeout: 15000 })).length).toBeGreaterThan(0);

    await user.click(screen.getByRole("button", { name: "Stop recording" }));
    await waitFor(() => {
      expect(screen.getByText("Idle")).toBeInTheDocument();
    });
  }, 20000);

  it("publishes and renders the hosted viewer projection after session sync", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getAllByRole("button", { name: /Session/ })[0]);
    await user.click(screen.getByRole("button", { name: /Attach local session|Refresh attach session/ }));
    await user.click(screen.getByRole("button", { name: "Sync relay snapshot" }));

    expect(await screen.findByText("Hosted viewer snapshot")).toBeInTheDocument();
    expect((await screen.findAllByText(/published/i, {}, { timeout: 5000 })).length).toBeGreaterThan(0);
    expect(screen.getAllByText("view-session-123").length).toBeGreaterThan(0);
    expect(screen.getByText("Hosted viewer members")).toBeInTheDocument();
    expect(screen.getByText("Hosted publication history")).toBeInTheDocument();
    expect(screen.getAllByText("sync-123").length).toBeGreaterThan(0);
    expect(screen.getAllByText("member-1").length).toBeGreaterThan(0);
  }, 20000);

  it("tracks hosted viewer membership separately from publication history", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getAllByRole("button", { name: /Session/ })[0]);
    await user.click(screen.getByRole("button", { name: /Attach local session|Refresh attach session/ }));
    await user.click(screen.getByRole("button", { name: "Sync relay snapshot" }));
    await user.click(screen.getByRole("button", { name: "Add hosted viewer" }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/hosted-view/members")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getAllByText("viewer-1").length).toBeGreaterThan(0);
    });

    await user.click(screen.getAllByRole("button", { name: "Remove hosted viewer" })[0]);

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/hosted-view/members")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "DELETE",
      )).toBe(true);
      expect(screen.getAllByText("viewer-1").length).toBe(1);
      expect(screen.getByText("Hosted publication history")).toBeInTheDocument();
    });
  });

  it("renders the multi developer workspace state in the session tab", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getAllByRole("button", { name: /Session/ })[0]);
    await user.click(screen.getByRole("button", { name: /Attach local session|Refresh attach session/ }));
    await user.click(screen.getByRole("button", { name: "Issue share token" }));
    await user.type(screen.getByPlaceholderText("paste share token"), "share_123456789abc");
    await user.click(screen.getByRole("button", { name: "Validate token" }));

    expect(await screen.findByText("Multi developer view")).toBeInTheDocument();
    expect(screen.getByText("lease-123")).toBeInTheDocument();
    expect(screen.getByText("https://relay.spring-devtools-ui.dev/view/session-123")).toBeInTheDocument();
    expect((await screen.findAllByText("member-1")).length).toBeGreaterThan(0);
    expect(screen.getByText("validated access")).toBeInTheDocument();
  });

  it("transfers relay ownership to a relay-owned viewer session", async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getAllByRole("button", { name: /Session/ })[0]);
    await user.click(screen.getByRole("button", { name: /Attach local session|Refresh attach session/ }));
    expect(await screen.findByText("Relay identity and ownership")).toBeInTheDocument();
    expect(screen.getAllByText("qa-viewer").length).toBeGreaterThan(0);

    await user.selectOptions(screen.getByRole("combobox", { name: /Transfer relay ownership/i }), "viewer-1");
    await user.click(screen.getByRole("button", { name: "Transfer relay owner" }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/owner-transfer")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getAllByText("acct-viewer-1").length).toBeGreaterThan(0);
    });
  });

  it("requests the next page from the server when pagination advances", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Requests/ })[0]);
    await user.click(screen.getAllByRole("button", { name: "Next" }).find((button) => !button.hasAttribute("disabled"))!);

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url]) =>
        String(url).includes("/_dev/api/requests?offset=25&limit=25"),
      )).toBe(true);
    });
  });

  it("shows a loading status while the first refresh is still pending", () => {
    vi.stubGlobal("fetch", vi.fn(() => new Promise(() => {})));

    render(<App />);

    expect(screen.getByText("loading")).toBeInTheDocument();
    expect(screen.getByText("last sync pending")).toBeInTheDocument();
  });

  it("renders empty-state messaging when the current stream has no results", async () => {
    vi.stubGlobal("fetch", vi.fn((input: string | URL) => {
      const url = String(input);
      if (url.includes("/endpoints")) {
        return Promise.resolve(jsonResponse({ ...endpointPage, items: [], total: 0 }));
      }
      if (url.includes("/requests")) {
        return Promise.resolve(jsonResponse({ ...requestPage, items: [], total: 0 }));
      }
      if (url.includes("/config")) {
        return Promise.resolve(jsonResponse({ ...configPage, items: [], total: 0 }));
      }
      if (url.includes("/logs")) {
        return Promise.resolve(jsonResponse({ ...logPage, items: [], total: 0 }));
      }
      if (url.includes("/feature-flags")) {
        return Promise.resolve(jsonResponse({ ...featureFlagPage, items: [], total: 0 }));
      }
      if (url.includes("/dependencies")) {
        return Promise.resolve(jsonResponse({ ...dependencyPage, items: [], total: 0 }));
      }
      if (url.includes("/time")) {
        return Promise.resolve(jsonResponse({ ...timePage, items: [], total: 0 }));
      }
      if (url.includes("/session")) {
        return Promise.resolve(jsonResponse({ ...sessionPage, items: [], total: 0 }));
      }
      if (url.includes("/jobs")) {
        return Promise.resolve(jsonResponse({ ...jobPage, items: [], total: 0 }));
      }
      if (url.includes("/db-queries")) {
        return Promise.resolve(jsonResponse({ ...dbQueryPage, items: [], total: 0 }));
      }
      if (url.includes("/webhooks/targets")) {
        return Promise.resolve(jsonResponse({ ...webhookTargetPage, items: [], total: 0 }));
      }
      if (url.includes("/fake-services")) {
        return Promise.resolve(jsonResponse({ ...fakeServicesPage, items: [], total: 0 }));
      }
      return Promise.reject(new Error(`Unhandled fetch URL: ${url}`));
    }));

    render(<App />);

    expect((await screen.findAllByText("0 total")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("No records match the current filter.")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("Select an item to inspect details.")).length).toBeGreaterThan(0);
  });

  it("shows failure state details when the dashboard refresh fails", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("network down"))));

    render(<App />);

    expect(await screen.findByText("error")).toBeInTheDocument();
    expect(screen.getByText("network down")).toBeInTheDocument();
  });

  it("sends dedicated server-side filters for log level and logger", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Logs/ })[0]);
    await user.selectOptions(screen.getByRole("combobox"), "ERROR");
    await user.type(screen.getByPlaceholderText("Filter by logger name"), "com.devtools.Test");

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url]) =>
        String(url).includes("/_dev/api/logs?")
        && String(url).includes("level=ERROR")
        && String(url).includes("logger=com.devtools.Test"),
      )).toBe(true);
    });
  });

  it("renders the jobs panel from the server payload", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Jobs/ })[0]);

    expect(await screen.findByText("heartbeat()")).toBeInTheDocument();
    expect(screen.getAllByText("30000ms").length).toBeGreaterThan(0);
    expect(screen.getByText("Scheduled job")).toBeInTheDocument();
  });

  it("renders the dependency graph panel from the server payload", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Dependencies/ })[0]);

    expect(await screen.findByText("Dependency graph")).toBeInTheDocument();
    expect(screen.getAllByText("testController").length).toBeGreaterThan(0);
    expect(screen.getAllByText("TestController").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/jdbcProbe/).length).toBeGreaterThan(0);
  });

  it("applies and resets feature flag overrides from the panel", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Flags/ })[0]);

    expect(await screen.findByRole("heading", { name: "features.checkout" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Enable locally/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/feature-flags")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Reset override/ })).not.toBeDisabled();
    });

    await user.click(screen.getByRole("button", { name: /Reset override/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/feature-flags?key=features.checkout")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "DELETE",
      )).toBe(true);
    });
  });

  it("saves and compares config snapshots from the panel", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Config/ })[0]);

    expect(await screen.findByText("Environment comparison")).toBeInTheDocument();
    const labelInput = screen.getByLabelText("Config snapshot label");
    await user.clear(labelInput);
    await user.type(labelInput, "Staging baseline");
    await user.click(screen.getByRole("button", { name: "Save snapshot" }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/config/snapshots")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST"
        && "body" in init
        && String(init.body).includes("Staging baseline"),
      )).toBe(true);
    });

    await user.selectOptions(screen.getByLabelText("Config snapshot selector"), "cfg_2");
    await user.click(screen.getByRole("button", { name: "Detect drift" }));
    expect(await screen.findByText("Drift status")).toBeInTheDocument();
    expect(screen.getByText("drift detected")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Compare to current" }));

    expect(await screen.findByText("Diff summary")).toBeInTheDocument();
    expect(screen.getAllByText("1 added").length).toBeGreaterThan(0);
    expect(screen.getAllByText("1 changed").length).toBeGreaterThan(0);
    expect(screen.getAllByText("feature.alpha").length).toBeGreaterThan(0);
    expect(screen.getAllByText("hello-now").length).toBeGreaterThan(0);
  });

  it("renders the database query panel from the server payload", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /DB Queries/ })[0]);

    expect(await screen.findByText("Database query")).toBeInTheDocument();
    expect(screen.getAllByText("select").length).toBeGreaterThan(0);
    expect(screen.getAllByText("dataSource").length).toBeGreaterThan(0);
  });

  it("sends an API test request through the simulator panel", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /API Testing/ })[0]);
    await user.click(screen.getByRole("button", { name: /Send request/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/api-testing/send")
        && typeof init === "object"
        && init !== null
        && "body" in init
        && String(init.body).includes("\"method\":\"POST\"")
        && String(init.body).includes("/webhooks/github"),
      )).toBe(true);
    });

    expect(await screen.findByText("POST /webhooks/github -> 200")).toBeInTheDocument();
  });

  it("enables a fake external service from the panel", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Fake Services/ })[0]);

    expect(await screen.findByRole("heading", { name: "GitHub Webhooks" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Enable stub/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/fake-services")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
    });
  });

  it("saves a fake service mock response from the panel", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Fake Services/ })[0]);

    expect(await screen.findByText("Mock response override")).toBeInTheDocument();
    await user.clear(screen.getByLabelText("Mock status"));
    await user.type(screen.getByLabelText("Mock status"), "418");
    await user.clear(screen.getByLabelText("Mock content type"));
    await user.type(screen.getByLabelText("Mock content type"), "application/problem+json");
    fireEvent.change(screen.getByLabelText("Mock response body"), { target: { value: "{\"error\":\"teapot\"}" } });
    await user.click(screen.getByRole("button", { name: /Save mock response/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/fake-services")
        && typeof init === "object"
        && init !== null
        && "body" in init
        && String(init.body).includes("\"routeId\":\"github:POST /_dev/fake/github/webhooks\"")
        && String(init.body).includes("\"status\":418")
        && String(init.body).includes("application/problem+json"),
      )).toBe(true);
    });
  });

  it("renders the audit logs panel from the server payload", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Audit Logs/ })[0]);

    expect(await screen.findByRole("heading", { name: "override.set" })).toBeInTheDocument();
    expect(screen.getAllByText("feature-flags").length).toBeGreaterThan(0);
    expect(screen.getByText("features.checkout=true")).toBeInTheDocument();
  });

  it("applies and resets a time override from the panel", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Time/ })[0]);

    expect(await screen.findByText("Time travel clock")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Apply time override/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/time")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Reset clock/ })).not.toBeDisabled();
    });

    expect(screen.getByText("Staging verification")).toBeInTheDocument();
    expect(screen.getByText("admin")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Reset clock/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/time")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "DELETE",
      )).toBe(true);
    });
  });

  it("attaches a local session and rotates its owner token", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect((await screen.findAllByText("/hello")).length).toBeGreaterThan(0);
    await user.click(screen.getAllByRole("button", { name: /Session/ })[0]);

    expect(await screen.findByText("Remote attach foundation")).toBeInTheDocument();
    const ownerInput = screen.getAllByRole("textbox")[0];
    await user.clear(ownerInput);
    await user.type(ownerInput, "pair-debugger");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: /Attach local session/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/attach")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Rotate owner token/ })).not.toBeDisabled();
      expect(screen.getByRole("button", { name: /Heartbeat relay/ })).not.toBeDisabled();
      expect(screen.getByRole("button", { name: /Sync relay snapshot/ })).not.toBeDisabled();
      expect(screen.getByText("healthy")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Open tunnel/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/tunnel/open")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getByText("tunnel-123")).toBeInTheDocument();
      expect(screen.getByText("open")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Close tunnel/ })).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Heartbeat relay/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/heartbeat")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
    });

    await user.click(screen.getByRole("button", { name: /Sync relay snapshot/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/sync")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getByText("synced")).toBeInTheDocument();
      expect(screen.getAllByText("sync-123").length).toBeGreaterThan(0);
    });

    await user.click(screen.getByRole("button", { name: /Rotate owner token/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/token")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
    });

    await user.selectOptions(screen.getAllByRole("combobox")[0], "guest");
    await user.click(screen.getByRole("button", { name: /Issue share token/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/share")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getAllByText("share_1…9abc").length).toBeGreaterThan(0);
    });

    await user.type(screen.getByPlaceholderText("paste share token"), "share_123456789abc");
    await user.click(screen.getByRole("button", { name: /Validate token/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/validate")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getAllByText("1").length).toBeGreaterThan(0);
      expect(screen.getAllByText("member-1").length).toBeGreaterThan(0);
      expect(screen.getAllByText("guest").length).toBeGreaterThan(0);
      expect(screen.getByText("POST /echo")).toBeInTheDocument();
      expect(screen.getByText("req_echo_1")).toBeInTheDocument();
      expect(screen.getByText("Team session viewer")).toBeInTheDocument();
      expect(screen.getAllByText("Recent actors").length).toBeGreaterThan(0);
      expect(screen.getByText("No shared artifact focus")).toBeInTheDocument();
      expect(screen.getByText("session.validation_succeeded")).toBeInTheDocument();
      expect(screen.getByText("Validation succeeded")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /Inspect request artifact/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/inspect")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url]) =>
        String(url).includes("/_dev/api/session/artifacts/request?requestId=req_echo_1"),
      )).toBe(true);
      expect(screen.getByText("Focused request artifact")).toBeInTheDocument();
      expect(screen.getAllByText("pair-debugger").length).toBeGreaterThan(0);
      expect(screen.getAllByText("request:req_echo_1").length).toBeGreaterThan(0);
    });

    await user.type(screen.getByPlaceholderText("Add a debugging observation, hypothesis, or next step"), "Investigate response mismatch before retrying");
    await user.click(screen.getByRole("button", { name: /Add debug note/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/notes")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getByText("Collaborative debugging")).toBeInTheDocument();
      expect(screen.getAllByText("Investigate response mismatch before retrying").length).toBeGreaterThan(0);
    });

    await user.click(screen.getByRole("button", { name: /Close tunnel/ }));

    await waitFor(() => {
      expect((fetch as ReturnType<typeof vi.fn>).mock.calls.some(([url, init]) =>
        String(url).includes("/_dev/api/session/tunnel/close")
        && typeof init === "object"
        && init !== null
        && "method" in init
        && init.method === "POST",
      )).toBe(true);
      expect(screen.getByText("closed")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Open tunnel/ })).toBeInTheDocument();
    });
  }, 10000);
});

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    statusText: "OK",
    json: async () => body,
  } as Response;
}

function noContentResponse(): Response {
  return {
    ok: true,
    status: 204,
    statusText: "No Content",
    json: async () => null,
  } as Response;
}
