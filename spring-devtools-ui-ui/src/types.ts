export type EndpointDescriptor = {
  method: string;
  path: string;
  controller: string;
  methodName: string;
};

export type CapturedRequest = {
  requestId: string;
  method: string;
  path: string;
  headers: Record<string, string[]>;
  body: string;
  bodyTruncated: boolean;
  binaryBody: boolean;
  timestamp: string;
  responseStatus: number;
};

export type ConfigPropertyDescriptor = {
  key: string;
  value: string;
  propertySource: string;
};

export type ConfigSnapshotDescriptor = {
  snapshotId: string;
  label: string;
  capturedAt: string;
  properties: ConfigPropertyDescriptor[];
};

export type ConfigDiffEntryDescriptor = {
  key: string;
  status: "ADDED" | "REMOVED" | "CHANGED" | "UNCHANGED";
  currentValue: string;
  currentPropertySource: string;
  snapshotValue: string;
  snapshotPropertySource: string;
};

export type ConfigComparisonDescriptor = {
  snapshot: ConfigSnapshotDescriptor;
  addedCount: number;
  removedCount: number;
  changedCount: number;
  unchangedCount: number;
  entries: ConfigDiffEntryDescriptor[];
};

export type ConfigDriftDescriptor = {
  available: boolean;
  comparedAt: string;
  snapshot: ConfigSnapshotDescriptor | null;
  drifted: boolean;
  totalChanges: number;
  addedCount: number;
  removedCount: number;
  changedCount: number;
  unchangedCount: number;
  entries: ConfigDiffEntryDescriptor[];
};

export type AuditLogEventDescriptor = {
  auditId: string;
  category: string;
  action: string;
  actor: string;
  detail: string;
  timestamp: string;
};

export type FeatureFlagDescriptor = {
  key: string;
  enabled: boolean;
  propertySource: string;
  overridden: boolean;
  definition?: FeatureFlagDefinitionDescriptor | null;
};

export type FeatureFlagDefinitionDescriptor = {
  key: string;
  displayName: string;
  description: string;
  owner: string;
  tags: string[];
  lifecycle: string;
  allowOverride: boolean;
  persisted: boolean;
  lastModifiedAt: string;
  lastModifiedBy: string;
};

export type DependencyNodeDescriptor = {
  beanName: string;
  beanType: string;
  scope: string;
  dependencies: string[];
  dependents: string[];
};

export type FakeExternalServiceDescriptor = {
  serviceId: string;
  displayName: string;
  description: string;
  basePath: string;
  enabled: boolean;
  routes: string[];
  mockResponses: FakeExternalServiceMockDescriptor[];
};

export type FakeExternalServiceMockDescriptor = {
  routeId: string;
  route: string;
  status: number;
  contentType: string;
  body: string;
};

export type TimeTravelStateDescriptor = {
  currentTime: string;
  zoneId: string;
  overridden: boolean;
  overrideReason: string | null;
  overriddenBy: string | null;
  overriddenAt: string | null;
  expiresAt: string | null;
};

export type RemoteSessionDescriptor = {
  sessionId: string;
  agentStatus: string;
  relayStatus: string;
  attached: boolean;
  relayUrl: string;
  shareUrl: string;
  activity: SessionActivityEventDescriptor[];
  replay: SessionReplayEntryDescriptor[];
  debugNotes: SessionDebugNoteDescriptor[];
  recordings: SessionRecordingDescriptor[];
  audit: SessionAuditEventDescriptor[];
  accessScope: string;
  allowedRoles: string[];
  activeMembers: SessionMemberDescriptor[];
  workspaceMembers: SessionWorkspaceMemberDescriptor[];
  ownerCount: number;
  viewerMemberCount: number;
  guestMemberCount: number;
  recentActors: string[];
  viewerCount: number;
  activeShareTokens: number;
  ownerName: string;
  relayOrganizationId: string | null;
  relayOrganizationName: string | null;
  relayOwnerAccountId: string | null;
  ownerTokenPreview: string;
  lastIssuedShareRole: string | null;
  lastIssuedShareTokenPreview: string;
  tokenMode: string;
  relayMode: string;
  relayHandshakeId: string | null;
  relayConnectionId: string | null;
  relayLeaseId: string | null;
  relayLeaseExpiresAt: string | null;
  relayViewerUrl: string | null;
  relayTunnelId: string | null;
  tunnelStatus: string;
  tunnelOpenedAt: string | null;
  tunnelClosedAt: string | null;
  lastError: string | null;
  focusedArtifactType: string | null;
  focusedArtifactId: string | null;
  focusedBy: string | null;
  focusedAt: string | null;
  recordingActive: boolean;
  currentRecordingId: string | null;
  recordingStartedAt: string | null;
  recordingStoppedAt: string | null;
  syncStatus: string;
  lastSyncId: string | null;
  lastSyncedAt: string | null;
  sessionVersion: number;
  publishedSessionVersion: number | null;
  hostedViewStatus: string;
  lastPublishedAt: string | null;
  activityRetentionLimit: number;
  replayRetentionLimit: number;
  recordingRetentionLimit: number;
  auditRetentionLimit: number;
  lastHeartbeatAt: string | null;
  nextHeartbeatAt: string | null;
  reconnectAt: string | null;
  lastRotatedAt: string;
  expiresAt: string;
};

export type HostedSessionViewDescriptor = {
  sessionId: string;
  viewId: string;
  syncId: string | null;
  available: boolean;
  durableState: string;
  sessionVersion: number;
  publishedVersion: number | null;
  relayViewerUrl: string | null;
  ownerName: string;
  accessScope: string;
  activeMemberCount: number;
  replayCount: number;
  debugNoteCount: number;
  recordingCount: number;
  focusedArtifactType: string | null;
  focusedArtifactId: string | null;
  lastPublishedAt: string | null;
  members: HostedSessionMemberDescriptor[];
  recentActors: string[];
  recentReplayTitles: string[];
};

export type HostedSessionMemberDescriptor = {
  memberId: string;
  role: string;
  source: string;
  joinedAt: string | null;
  publishedAt: string | null;
  focusedArtifactType: string | null;
  focusedArtifactId: string | null;
  lastAction: string;
};

export type RelayViewerSessionDescriptor = {
  viewerSessionId: string;
  role: string;
  viewerName: string;
  createdAt: string;
  expiresAt: string;
};

export type RelayOrganizationDescriptor = {
  organizationId: string;
  organizationName: string;
};

export type RelayAccountDescriptor = {
  accountId: string;
  displayName: string;
  organizationId: string;
  role: string;
};

export type RelaySessionIdentityDescriptor = {
  sessionId: string;
  organization: RelayOrganizationDescriptor | null;
  owner: RelayAccountDescriptor | null;
  collaborators: RelayAccountDescriptor[];
};

export type SessionMemberDescriptor = {
  memberId: string;
  role: string;
  source: string;
  joinedAt: string;
  lastSeenAt: string;
};

export type SessionWorkspaceMemberDescriptor = {
  memberId: string;
  role: string;
  source: string;
  joinedAt: string;
  lastSeenAt: string;
  focusedArtifactType: string | null;
  focusedArtifactId: string | null;
  lastAction: string;
};

export type SessionActivityEventDescriptor = {
  eventType: string;
  actor: string;
  detail: string;
  timestamp: string;
};

export type SessionReplayEntryDescriptor = {
  replayId: string;
  category: string;
  title: string;
  payloadPreview: string;
  artifactType: string | null;
  artifactId: string | null;
  occurredAt: string;
};

export type SessionDebugNoteDescriptor = {
  noteId: string;
  author: string;
  message: string;
  artifactType: string | null;
  artifactId: string | null;
  createdAt: string;
};

export type SessionRecordingDescriptor = {
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
};

export type SessionAuditEventDescriptor = {
  auditId: string;
  eventType: string;
  actor: string;
  detail: string;
  recordedAt: string;
};

export type SessionInspectArtifactRequest = {
  artifactType: string;
  artifactId: string;
  actor: string;
};

export type SessionDebugNoteRequest = {
  author: string;
  message: string;
  artifactType: string | null;
  artifactId: string | null;
};

export type SessionRecordingRequest = {
  actor: string;
};

export type HostedSessionMemberRequest = {
  memberId: string;
  role: string;
  source: string;
  actor: string;
};

export type SessionOwnerTransferRequest = {
  targetViewerSessionId: string;
};

export type SessionShareTokenDescriptor = {
  role: string;
  tokenPreview: string;
  shareUrl: string;
  expiresAt: string;
  active: boolean;
};

export type SessionAccessValidationDescriptor = {
  allowed: boolean;
  role: string;
  reason: string;
  viewerCount: number;
  sessionId: string;
};

export type LogEventDescriptor = {
  timestamp: string;
  level: string;
  message: string;
  logger: string;
  stackTrace: string | null;
};

export type JobDescriptor = {
  beanName: string;
  beanType: string;
  methodName: string;
  triggerType: string;
  expression: string;
  scheduler: string;
};

export type DbQueryDescriptor = {
  timestamp: string;
  sql: string;
  statementType: string;
  dataSource: string;
  rowsAffected: number | null;
};

export type WebhookTargetDescriptor = {
  method: string;
  path: string;
  controller: string;
  methodName: string;
};

export type WebhookDeliveryResult = {
  method: string;
  path: string;
  status: number;
  responseBody: string;
};

export type ApiTestResult = {
  method: string;
  path: string;
  status: number;
  responseBody: string;
};

export type ErrorReplayResult = {
  requestId: string;
  method: string;
  path: string;
  originalStatus: number;
  replayStatus: number;
  replayedAt: string;
  responseBody: string;
};

export type PagedResponse<T> = {
  items: T[];
  total: number;
  offset: number;
  limit: number;
};

export type TabId = "endpoints" | "requests" | "config" | "featureFlags" | "dependencies" | "time" | "session" | "logs" | "jobs" | "dbQueries" | "webhooks" | "fakeServices" | "auditLogs";
