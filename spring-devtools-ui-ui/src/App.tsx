import { startTransition, useDeferredValue, useEffect, useState } from "react";
import type { ReactNode } from "react";
import type {
  AuditLogEventDescriptor,
  CapturedRequest,
  ConfigComparisonDescriptor,
  ConfigDiffEntryDescriptor,
  ConfigDriftDescriptor,
  ConfigPropertyDescriptor,
  ConfigSnapshotDescriptor,
  DbQueryDescriptor,
  DependencyNodeDescriptor,
  ErrorReplayResult,
  EndpointDescriptor,
  FakeExternalServiceDescriptor,
  FakeExternalServiceMockDescriptor,
  FeatureFlagDescriptor,
  FeatureFlagDefinitionDescriptor,
  HostedSessionMemberRequest,
  HostedSessionViewDescriptor,
  JobDescriptor,
  LogEventDescriptor,
  PagedResponse,
  RelaySessionIdentityDescriptor,
  RemoteSessionDescriptor,
  RelayViewerSessionDescriptor,
  SessionAccessValidationDescriptor,
  SessionDebugNoteRequest,
  SessionRecordingRequest,
  SessionInspectArtifactRequest,
  SessionOwnerTransferRequest,
  SessionShareTokenDescriptor,
  TabId,
  TimeTravelStateDescriptor,
  ApiTestResult,
  WebhookTargetDescriptor,
} from "./types";

const API_BASE = "/_dev/api";
const REFRESH_INTERVAL_MS = 5000;
const PAGE_SIZE = 25;
const ONBOARDING_STORAGE_KEY = "falkenr.onboarding.v1.seen";

const TABS: Array<{
  id: TabId;
  label: string;
  blurb: string;
}> = [
  { id: "endpoints", label: "Endpoints", blurb: "Mapped controllers and request methods." },
  { id: "requests", label: "Requests", blurb: "Last 100 in-memory HTTP exchanges." },
  { id: "config", label: "Config", blurb: "Resolved environment values and sources." },
  { id: "featureFlags", label: "Flags", blurb: "Local-only feature state overrides injected into the Spring environment." },
  { id: "dependencies", label: "Dependencies", blurb: "Live Spring bean relationships from the running application context." },
  { id: "time", label: "Time", blurb: "Override the injected application clock for local time travel debugging." },
  { id: "session", label: "Session", blurb: "Prepare a local agent session for future remote attach and team sharing." },
  { id: "logs", label: "Logs", blurb: "Recent logback events captured in memory." },
  { id: "jobs", label: "Jobs", blurb: "Scheduled methods discovered from the local runtime." },
  { id: "dbQueries", label: "DB Queries", blurb: "Recent JDBC statements observed from the local datasource." },
  { id: "webhooks", label: "API Testing", blurb: "Send ad hoc requests into the running local application." },
  { id: "fakeServices", label: "Fake Services", blurb: "Enable canned local stubs for external HTTP dependencies." },
  { id: "auditLogs", label: "Audit Logs", blurb: "Environment-level admin actions for governance and review." },
];

type TabData = {
  endpoints: PagedResponse<EndpointDescriptor>;
  requests: PagedResponse<CapturedRequest>;
  config: PagedResponse<ConfigPropertyDescriptor>;
  featureFlags: PagedResponse<FeatureFlagDescriptor>;
  dependencies: PagedResponse<DependencyNodeDescriptor>;
  logs: PagedResponse<LogEventDescriptor>;
  session: PagedResponse<RemoteSessionDescriptor>;
  jobs: PagedResponse<JobDescriptor>;
  dbQueries: PagedResponse<DbQueryDescriptor>;
  webhooks: PagedResponse<WebhookTargetDescriptor>;
  fakeServices: PagedResponse<FakeExternalServiceDescriptor>;
  auditLogs: PagedResponse<AuditLogEventDescriptor>;
  time: PagedResponse<TimeTravelStateDescriptor>;
};

const EMPTY_PAGE = {
  items: [],
  total: 0,
  offset: 0,
  limit: PAGE_SIZE,
};

const EMPTY_DATA: TabData = {
  endpoints: EMPTY_PAGE,
  requests: EMPTY_PAGE,
  config: EMPTY_PAGE,
  featureFlags: EMPTY_PAGE,
  dependencies: EMPTY_PAGE,
  session: EMPTY_PAGE,
  logs: EMPTY_PAGE,
  jobs: EMPTY_PAGE,
  dbQueries: EMPTY_PAGE,
  webhooks: EMPTY_PAGE,
  fakeServices: EMPTY_PAGE,
  auditLogs: EMPTY_PAGE,
  time: EMPTY_PAGE,
};

function isOnDevRoute() {
  try {
    return typeof window !== "undefined" && window.location.pathname.startsWith("/_dev");
  } catch {
    return false;
  }
}

function hasSeenOnboarding() {
  try {
    return typeof window !== "undefined" && window.localStorage.getItem(ONBOARDING_STORAGE_KEY) === "1";
  } catch {
    return true;
  }
}

function markOnboardingSeen() {
  try {
    window.localStorage.setItem(ONBOARDING_STORAGE_KEY, "1");
  } catch {
    // Ignore.
  }
}

type SelectionState = Record<TabId, string | null>;
type OffsetState = Record<TabId, number>;
type LogFilterState = {
  level: string;
  logger: string;
};

const EMPTY_SELECTIONS: SelectionState = {
  endpoints: null,
  requests: null,
  config: null,
  featureFlags: null,
  dependencies: null,
  session: null,
  logs: null,
  jobs: null,
  dbQueries: null,
  webhooks: null,
  fakeServices: null,
  auditLogs: null,
  time: null,
};

const EMPTY_OFFSETS: OffsetState = {
  endpoints: 0,
  requests: 0,
  config: 0,
  featureFlags: 0,
  dependencies: 0,
  session: 0,
  logs: 0,
  jobs: 0,
  dbQueries: 0,
  webhooks: 0,
  fakeServices: 0,
  auditLogs: 0,
  time: 0,
};

const EMPTY_LOG_FILTERS: LogFilterState = {
  level: "",
  logger: "",
};

export default function App() {
  const [data, setData] = useState<TabData>(EMPTY_DATA);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabId>("endpoints");
  const [onboardingStep, setOnboardingStep] = useState<number | null>(() => {
    if (!isOnDevRoute()) return null;
    return hasSeenOnboarding() ? null : 0;
  });
  const [selections, setSelections] = useState<SelectionState>(EMPTY_SELECTIONS);
  const [offsets, setOffsets] = useState<OffsetState>(EMPTY_OFFSETS);
  const [search, setSearch] = useState("");
  const [logFilters, setLogFilters] = useState<LogFilterState>(EMPTY_LOG_FILTERS);
  const [featureFlagPendingKey, setFeatureFlagPendingKey] = useState<string | null>(null);
  const [featureFlagError, setFeatureFlagError] = useState<string | null>(null);
  const [featureFlagDefinitionDraft, setFeatureFlagDefinitionDraft] = useState({
    displayName: "",
    description: "",
    owner: "",
    tags: "",
    lifecycle: "active",
    allowOverride: true,
  });
  const [errorReplayPending, setErrorReplayPending] = useState(false);
  const [errorReplayError, setErrorReplayError] = useState<string | null>(null);
  const [errorReplayResult, setErrorReplayResult] = useState<ErrorReplayResult | null>(null);
  const [configSnapshotLabel, setConfigSnapshotLabel] = useState("Local baseline");
  const [configSnapshots, setConfigSnapshots] = useState<ConfigSnapshotDescriptor[]>([]);
  const [selectedConfigSnapshotId, setSelectedConfigSnapshotId] = useState("");
  const [configComparison, setConfigComparison] = useState<ConfigComparisonDescriptor | null>(null);
  const [configDrift, setConfigDrift] = useState<ConfigDriftDescriptor | null>(null);
  const [configPending, setConfigPending] = useState(false);
  const [configError, setConfigError] = useState<string | null>(null);
  const [fakeServicePendingId, setFakeServicePendingId] = useState<string | null>(null);
  const [fakeServiceError, setFakeServiceError] = useState<string | null>(null);
  const [fakeServiceMockRouteId, setFakeServiceMockRouteId] = useState("");
  const [fakeServiceMockStatus, setFakeServiceMockStatus] = useState("200");
  const [fakeServiceMockContentType, setFakeServiceMockContentType] = useState("application/json");
  const [fakeServiceMockBody, setFakeServiceMockBody] = useState("{\n  \"mocked\": true\n}");
  const [timeError, setTimeError] = useState<string | null>(null);
  const [timePending, setTimePending] = useState(false);
  const [timeInput, setTimeInput] = useState("2026-04-08T16:30:00Z");
  const [timeZoneInput, setTimeZoneInput] = useState("UTC");
  const [timeReasonInput, setTimeReasonInput] = useState("Staging verification");
  const [timeDurationInput, setTimeDurationInput] = useState("30");
  const [sessionOwnerName, setSessionOwnerName] = useState("local-developer");
  const [sessionAllowGuests, setSessionAllowGuests] = useState(false);
  const [sessionShareRole, setSessionShareRole] = useState("viewer");
  const [sessionValidateToken, setSessionValidateToken] = useState("");
  const [sessionShareResult, setSessionShareResult] = useState<SessionShareTokenDescriptor | null>(null);
  const [sessionValidationResult, setSessionValidationResult] = useState<SessionAccessValidationDescriptor | null>(null);
  const [sessionPending, setSessionPending] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [sessionArtifact, setSessionArtifact] = useState<CapturedRequest | null>(null);
  const [hostedSessionView, setHostedSessionView] = useState<HostedSessionViewDescriptor | null>(null);
  const [hostedSessionHistory, setHostedSessionHistory] = useState<HostedSessionViewDescriptor[]>([]);
  const [relayViewerSessions, setRelayViewerSessions] = useState<RelayViewerSessionDescriptor[]>([]);
  const [relaySessionIdentity, setRelaySessionIdentity] = useState<RelaySessionIdentityDescriptor | null>(null);
  const [ownerTransferTarget, setOwnerTransferTarget] = useState("");
  const [sessionNoteDraft, setSessionNoteDraft] = useState("");
  const [hostedViewerMemberId, setHostedViewerMemberId] = useState("viewer-1");
  const [hostedViewerRole, setHostedViewerRole] = useState("viewer");
  const [hostedViewerSource, setHostedViewerSource] = useState("relay-viewer");
  const [apiTestMethod, setApiTestMethod] = useState("POST");
  const [webhookPath, setWebhookPath] = useState("");
  const [webhookBody, setWebhookBody] = useState("{\n  \"event\": \"demo\",\n  \"source\": \"spring-devtools-ui\"\n}");
  const [webhookHeaders, setWebhookHeaders] = useState("{\n  \"X-Webhook-Source\": \"spring-devtools-ui\"\n}");
  const [webhookResult, setWebhookResult] = useState<ApiTestResult | null>(null);
  const [webhookError, setWebhookError] = useState<string | null>(null);
  const [webhookSending, setWebhookSending] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<string>("");
  const deferredSearch = useDeferredValue(search.trim());
  const deferredLoggerFilter = useDeferredValue(logFilters.logger.trim());

  useEffect(() => {
    let cancelled = false;

    async function refresh() {
      try {
        if (!cancelled && status === "loading") {
          setError(null);
        }

        const [endpoints, requests, config, configSnapshotsPage, featureFlags, dependencies, time, session, hostedView, hostedHistory, viewerSessions, sessionIdentity, logs, jobs, dbQueries, webhooks, fakeServices, auditLogs] = await Promise.all([
          readJson<PagedResponse<EndpointDescriptor>>("/endpoints", { q: deferredSearch, offset: offsets.endpoints, limit: PAGE_SIZE }),
          readJson<PagedResponse<CapturedRequest>>("/requests", { q: deferredSearch, offset: offsets.requests, limit: PAGE_SIZE }),
          readJson<PagedResponse<ConfigPropertyDescriptor>>("/config", { q: deferredSearch, offset: offsets.config, limit: PAGE_SIZE }),
          readJson<PagedResponse<ConfigSnapshotDescriptor>>("/config/snapshots", { offset: 0, limit: 10 }),
          readJson<PagedResponse<FeatureFlagDescriptor>>("/feature-flags", {
            q: deferredSearch,
            offset: offsets.featureFlags,
            limit: PAGE_SIZE,
          }),
          readJson<PagedResponse<DependencyNodeDescriptor>>("/dependencies", {
            q: deferredSearch,
            offset: offsets.dependencies,
            limit: PAGE_SIZE,
          }),
          readJson<PagedResponse<TimeTravelStateDescriptor>>("/time", { offset: offsets.time, limit: 1 }),
          readJson<PagedResponse<RemoteSessionDescriptor>>("/session", { offset: offsets.session, limit: 1 }),
          readJson<HostedSessionViewDescriptor>("/session/hosted-view"),
          readJson<PagedResponse<HostedSessionViewDescriptor>>("/session/hosted-history", { offset: 0, limit: 5 }),
          readJson<PagedResponse<RelayViewerSessionDescriptor>>("/session/viewer-sessions", { offset: 0, limit: 10 }),
          readJson<RelaySessionIdentityDescriptor>("/session/identity"),
          readJson<PagedResponse<LogEventDescriptor>>("/logs", {
            q: deferredSearch,
            level: logFilters.level,
            logger: deferredLoggerFilter,
            offset: offsets.logs,
            limit: PAGE_SIZE,
          }),
          readJson<PagedResponse<JobDescriptor>>("/jobs", { q: deferredSearch, offset: offsets.jobs, limit: PAGE_SIZE }),
          readJson<PagedResponse<DbQueryDescriptor>>("/db-queries", { q: deferredSearch, offset: offsets.dbQueries, limit: PAGE_SIZE }),
          readJson<PagedResponse<WebhookTargetDescriptor>>("/webhooks/targets", { q: deferredSearch, offset: offsets.webhooks, limit: PAGE_SIZE }),
          readJson<PagedResponse<FakeExternalServiceDescriptor>>("/fake-services", { q: deferredSearch, offset: offsets.fakeServices, limit: PAGE_SIZE }),
          readJson<PagedResponse<AuditLogEventDescriptor>>("/audit-logs", { q: deferredSearch, offset: offsets.auditLogs, limit: PAGE_SIZE }),
        ]);

        if (cancelled) {
          return;
        }

        startTransition(() => {
          const nextData = { endpoints, requests, config, featureFlags, dependencies, time, session, logs, jobs, dbQueries, webhooks, fakeServices, auditLogs };
          setData(nextData);
          setHostedSessionView(hostedView);
          setHostedSessionHistory(hostedHistory.items);
          setRelayViewerSessions(viewerSessions.items);
          setRelaySessionIdentity(sessionIdentity);
          setConfigSnapshots(configSnapshotsPage.items);
          setSelectedConfigSnapshotId((previous) => previous || configSnapshotsPage.items[0]?.snapshotId || "");
          setOwnerTransferTarget((previous) => previous || viewerSessions.items[0]?.viewerSessionId || "");
          setStatus("ready");
          setLastUpdated(new Date().toLocaleTimeString());
          setSelections((previous) => ensureSelections(previous, nextData));
          setWebhookPath((previous) => previous || webhooks.items[0]?.path || "");
        });

        if (configSnapshotsPage.items.length > 0) {
          const drift = await readJson<ConfigDriftDescriptor>("/config/drift", { snapshotId: configSnapshotsPage.items[0].snapshotId });
          if (!cancelled) {
            setConfigDrift(drift);
          }
        } else if (!cancelled) {
          setConfigDrift(null);
        }
      } catch (refreshError) {
        if (cancelled) {
          return;
        }

        const message =
          refreshError instanceof Error ? refreshError.message : "Failed to load dashboard data.";
        setStatus("error");
        setError(message);
      }
    }

    refresh();
    const intervalId = window.setInterval(refresh, REFRESH_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [deferredLoggerFilter, deferredSearch, logFilters.level, offsets]);

  const currentItems = {
    endpoints: data.endpoints.items,
    requests: data.requests.items,
    config: data.config.items,
    featureFlags: data.featureFlags.items,
    dependencies: data.dependencies.items,
    time: data.time.items,
    session: data.session.items,
    logs: data.logs.items,
    jobs: data.jobs.items,
    dbQueries: data.dbQueries.items,
    webhooks: data.webhooks.items,
    fakeServices: data.fakeServices.items,
    auditLogs: data.auditLogs.items,
  };

  const activeKey = selections[activeTab];
  const activeItem = resolveActiveItem(activeTab, currentItems, activeKey);
  const activePage = data[activeTab];
  const activePageStart = activePage.total === 0 ? 0 : activePage.offset + 1;
  const activePageEnd = activePage.offset + activePage.items.length;
  const canGoPrevious = activePage.offset > 0;
  const canGoNext = activePage.offset + activePage.items.length < activePage.total;

  async function sendWebhook() {
    setWebhookSending(true);
    setWebhookError(null);
    try {
      const parsedHeaders = webhookHeaders.trim() ? (JSON.parse(webhookHeaders) as Record<string, string>) : {};
      const response = await fetch(`${API_BASE}/api-testing/send`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          method: apiTestMethod,
          path: webhookPath,
          body: webhookBody,
          headers: parsedHeaders,
        }),
      });

      if (!response.ok) {
        throw new Error(`API test request failed: ${response.status} ${response.statusText}`);
      }

      const result = (await response.json()) as ApiTestResult;
      setWebhookResult(result);
    } catch (sendError) {
      setWebhookError(sendError instanceof Error ? sendError.message : "API test request failed.");
    } finally {
      setWebhookSending(false);
    }
  }

  async function updateFeatureFlag(key: string, enabled: boolean) {
    setFeatureFlagPendingKey(key);
    setFeatureFlagError(null);
    try {
      const response = await fetch(`${API_BASE}/feature-flags`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ key, enabled }),
      });

      if (!response.ok) {
        throw new Error(`Feature flag update failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (updateError) {
      setFeatureFlagError(updateError instanceof Error ? updateError.message : "Feature flag update failed.");
    } finally {
      setFeatureFlagPendingKey(null);
    }
  }

  async function saveFeatureFlagDefinition(key: string) {
    setFeatureFlagPendingKey(key);
    setFeatureFlagError(null);
    try {
      const response = await fetch(`${API_BASE}/feature-flags/definitions`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          key,
          displayName: featureFlagDefinitionDraft.displayName,
          description: featureFlagDefinitionDraft.description,
          owner: featureFlagDefinitionDraft.owner,
          tags: featureFlagDefinitionDraft.tags
            .split(",")
            .map((tag) => tag.trim())
            .filter(Boolean),
          lifecycle: featureFlagDefinitionDraft.lifecycle,
          allowOverride: featureFlagDefinitionDraft.allowOverride,
        }),
      });

      if (!response.ok) {
        throw new Error(`Feature flag definition save failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (definitionError) {
      setFeatureFlagError(definitionError instanceof Error ? definitionError.message : "Feature flag definition save failed.");
    } finally {
      setFeatureFlagPendingKey(null);
    }
  }

  async function deleteFeatureFlagDefinition(key: string) {
    setFeatureFlagPendingKey(key);
    setFeatureFlagError(null);
    try {
      const response = await fetch(`${API_BASE}/feature-flags/definitions?key=${encodeURIComponent(key)}`, {
        method: "DELETE",
      });

      if (!response.ok) {
        throw new Error(`Feature flag definition delete failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (definitionError) {
      setFeatureFlagError(definitionError instanceof Error ? definitionError.message : "Feature flag definition delete failed.");
    } finally {
      setFeatureFlagPendingKey(null);
    }
  }

  async function replayErrorRequest(requestId: string) {
    setErrorReplayPending(true);
    setErrorReplayError(null);
    try {
      const params = new URLSearchParams({ requestId });
      const response = await fetch(`${API_BASE}/requests/replay?${params.toString()}`, {
        method: "POST",
        headers: {
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        throw new Error(`Error replay failed: ${response.status} ${response.statusText}`);
      }
      const result = (await response.json()) as ErrorReplayResult;
      setErrorReplayResult(result);
      setOffsets((previous) => ({ ...previous }));
    } catch (replayError) {
      setErrorReplayError(replayError instanceof Error ? replayError.message : "Error replay failed.");
    } finally {
      setErrorReplayPending(false);
    }
  }

  async function createConfigSnapshot() {
    setConfigPending(true);
    setConfigError(null);
    try {
      const response = await fetch(`${API_BASE}/config/snapshots`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ label: configSnapshotLabel }),
      });

      if (!response.ok) {
        throw new Error(`Config snapshot failed: ${response.status} ${response.statusText}`);
      }

      const snapshot = (await response.json()) as ConfigSnapshotDescriptor;
      setConfigSnapshots((previous) => [snapshot, ...previous.filter((item) => item.snapshotId !== snapshot.snapshotId)]);
      setSelectedConfigSnapshotId(snapshot.snapshotId);
      const drift = await readJson<ConfigDriftDescriptor>("/config/drift", { snapshotId: snapshot.snapshotId });
      setConfigDrift(drift);
      setConfigComparison(null);
    } catch (snapshotError) {
      setConfigError(snapshotError instanceof Error ? snapshotError.message : "Config snapshot failed.");
    } finally {
      setConfigPending(false);
    }
  }

  async function compareConfigSnapshot(snapshotId: string) {
    if (!snapshotId) {
      return;
    }
    setConfigPending(true);
    setConfigError(null);
    try {
      const comparison = await readJson<ConfigComparisonDescriptor>("/config/compare", { snapshotId });
      setConfigComparison(comparison);
    } catch (comparisonError) {
      setConfigError(comparisonError instanceof Error ? comparisonError.message : "Config comparison failed.");
    } finally {
      setConfigPending(false);
    }
  }

  async function detectConfigDrift(snapshotId: string) {
    setConfigPending(true);
    setConfigError(null);
    try {
      const drift = await readJson<ConfigDriftDescriptor>("/config/drift", snapshotId ? { snapshotId } : undefined);
      setConfigDrift(drift);
      if (drift.snapshot) {
        setSelectedConfigSnapshotId(drift.snapshot.snapshotId);
      }
    } catch (driftError) {
      setConfigError(driftError instanceof Error ? driftError.message : "Config drift detection failed.");
    } finally {
      setConfigPending(false);
    }
  }

  async function clearFeatureFlag(key: string) {
    setFeatureFlagPendingKey(key);
    setFeatureFlagError(null);
    try {
      const response = await fetch(`${API_BASE}/feature-flags?key=${encodeURIComponent(key)}`, {
        method: "DELETE",
      });

      if (!response.ok) {
        throw new Error(`Feature flag reset failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (clearError) {
      setFeatureFlagError(clearError instanceof Error ? clearError.message : "Feature flag reset failed.");
    } finally {
      setFeatureFlagPendingKey(null);
    }
  }

  async function updateFakeService(serviceId: string, enabled: boolean) {
    setFakeServicePendingId(serviceId);
    setFakeServiceError(null);
    try {
      const response = await fetch(`${API_BASE}/fake-services`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ serviceId, enabled }),
      });

      if (!response.ok) {
        throw new Error(`Fake service update failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (updateError) {
      setFakeServiceError(updateError instanceof Error ? updateError.message : "Fake service update failed.");
    } finally {
      setFakeServicePendingId(null);
    }
  }

  async function updateFakeServiceMock(serviceId: string, routeId: string) {
    setFakeServicePendingId(serviceId);
    setFakeServiceError(null);
    try {
      const response = await fetch(`${API_BASE}/fake-services`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          serviceId,
          enabled: true,
          mockResponse: {
            serviceId,
            routeId,
            status: Number(fakeServiceMockStatus),
            contentType: fakeServiceMockContentType,
            body: fakeServiceMockBody,
          },
        }),
      });

      if (!response.ok) {
        throw new Error(`Fake service mock update failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (updateError) {
      setFakeServiceError(updateError instanceof Error ? updateError.message : "Fake service mock update failed.");
    } finally {
      setFakeServicePendingId(null);
    }
  }

  async function updateTime() {
    setTimePending(true);
    setTimeError(null);
    try {
      const response = await fetch(`${API_BASE}/time`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({
          instant: timeInput,
          zoneId: timeZoneInput,
          reason: timeReasonInput,
          durationMinutes: timeDurationInput.trim() ? Number(timeDurationInput) : null,
        }),
      });

      if (!response.ok) {
        throw new Error(`Time override failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (updateError) {
      setTimeError(updateError instanceof Error ? updateError.message : "Time override failed.");
    } finally {
      setTimePending(false);
    }
  }

  async function resetTime() {
    setTimePending(true);
    setTimeError(null);
    try {
      const response = await fetch(`${API_BASE}/time`, { method: "DELETE" });

      if (!response.ok) {
        throw new Error(`Time reset failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (resetError) {
      setTimeError(resetError instanceof Error ? resetError.message : "Time reset failed.");
    } finally {
      setTimePending(false);
    }
  }

  async function attachSession() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/attach`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ ownerName: sessionOwnerName, allowGuests: sessionAllowGuests }),
      });

      if (!response.ok) {
        throw new Error(`Session attach failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (attachError) {
      setSessionError(attachError instanceof Error ? attachError.message : "Session attach failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function rotateSessionToken() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/token`, { method: "POST" });

      if (!response.ok) {
        throw new Error(`Token rotation failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (rotateError) {
      setSessionError(rotateError instanceof Error ? rotateError.message : "Token rotation failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function heartbeatSession() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/heartbeat`, { method: "POST" });

      if (!response.ok) {
        throw new Error(`Session heartbeat failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (heartbeatError) {
      setSessionError(heartbeatError instanceof Error ? heartbeatError.message : "Session heartbeat failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function syncSession() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/sync`, { method: "POST" });

      if (!response.ok) {
        throw new Error(`Session sync failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (syncError) {
      setSessionError(syncError instanceof Error ? syncError.message : "Session sync failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function revokeSession() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session`, { method: "DELETE" });

      if (!response.ok) {
        throw new Error(`Session revoke failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (revokeError) {
      setSessionError(revokeError instanceof Error ? revokeError.message : "Session revoke failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function openSessionTunnel() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/tunnel/open`, { method: "POST" });
      if (!response.ok) {
        throw new Error(`Session tunnel open failed: ${response.status} ${response.statusText}`);
      }
      setOffsets((previous) => ({ ...previous }));
    } catch (tunnelError) {
      setSessionError(tunnelError instanceof Error ? tunnelError.message : "Session tunnel open failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function closeSessionTunnel() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/tunnel/close`, { method: "POST" });
      if (!response.ok) {
        throw new Error(`Session tunnel close failed: ${response.status} ${response.statusText}`);
      }
      setOffsets((previous) => ({ ...previous }));
    } catch (tunnelError) {
      setSessionError(tunnelError instanceof Error ? tunnelError.message : "Session tunnel close failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function issueSessionShareToken() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/share`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ role: sessionShareRole }),
      });

      if (!response.ok) {
        throw new Error(`Session share failed: ${response.status} ${response.statusText}`);
      }

      const result = (await response.json()) as SessionShareTokenDescriptor;
      setSessionShareResult(result);
      setOffsets((previous) => ({ ...previous }));
    } catch (shareError) {
      setSessionError(shareError instanceof Error ? shareError.message : "Session share failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function validateSessionShareToken() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const response = await fetch(`${API_BASE}/session/validate`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ token: sessionValidateToken }),
      });

      if (!response.ok) {
        throw new Error(`Session validation failed: ${response.status} ${response.statusText}`);
      }

      const result = (await response.json()) as SessionAccessValidationDescriptor;
      setSessionValidationResult(result);
      setOffsets((previous) => ({ ...previous }));
    } catch (validateError) {
      setSessionError(validateError instanceof Error ? validateError.message : "Session validation failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function inspectSessionRequestArtifact(entryRequestId: string) {
    setSessionPending(true);
    setSessionError(null);
    try {
      const inspectPayload: SessionInspectArtifactRequest = {
        artifactType: "request",
        artifactId: entryRequestId,
        actor: sessionOwnerName,
      };
      const inspectResponse = await fetch(`${API_BASE}/session/inspect`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(inspectPayload),
      });
      if (!inspectResponse.ok) {
        throw new Error(`Session inspect failed: ${inspectResponse.status} ${inspectResponse.statusText}`);
      }

      const artifact = await readJson<CapturedRequest>("/session/artifacts/request", { requestId: entryRequestId });
      setSessionArtifact(artifact);
      setOffsets((previous) => ({ ...previous }));
    } catch (inspectError) {
      setSessionError(inspectError instanceof Error ? inspectError.message : "Session inspect failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function addHostedViewerMember() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const payload: HostedSessionMemberRequest = {
        memberId: hostedViewerMemberId,
        role: hostedViewerRole,
        source: hostedViewerSource,
        actor: sessionOwnerName,
      };
      const response = await fetch(`${API_BASE}/session/hosted-view/members`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        throw new Error(`Hosted viewer add failed: ${response.status} ${response.statusText}`);
      }
      setOffsets((previous) => ({ ...previous }));
    } catch (hostedMemberError) {
      setSessionError(hostedMemberError instanceof Error ? hostedMemberError.message : "Hosted viewer add failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function removeHostedViewerMember(memberId: string) {
    setSessionPending(true);
    setSessionError(null);
    try {
      const params = new URLSearchParams({ memberId, actor: sessionOwnerName });
      const response = await fetch(`${API_BASE}/session/hosted-view/members?${params.toString()}`, {
        method: "DELETE",
        headers: {
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        throw new Error(`Hosted viewer removal failed: ${response.status} ${response.statusText}`);
      }
      setOffsets((previous) => ({ ...previous }));
    } catch (hostedMemberError) {
      setSessionError(hostedMemberError instanceof Error ? hostedMemberError.message : "Hosted viewer removal failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function revokeRelayViewerSession(viewerSessionId: string) {
    setSessionPending(true);
    setSessionError(null);
    try {
      const params = new URLSearchParams({ viewerSessionId, offset: "0", limit: "10" });
      const response = await fetch(`${API_BASE}/session/viewer-sessions?${params.toString()}`, {
        method: "DELETE",
        headers: {
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        throw new Error(`Viewer session revoke failed: ${response.status} ${response.statusText}`);
      }
      const payload = (await response.json()) as PagedResponse<RelayViewerSessionDescriptor>;
      setRelayViewerSessions(payload.items);
      setOwnerTransferTarget((previous) =>
        previous === viewerSessionId ? payload.items[0]?.viewerSessionId ?? "" : previous,
      );
    } catch (viewerSessionError) {
      setSessionError(viewerSessionError instanceof Error ? viewerSessionError.message : "Viewer session revoke failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function transferSessionOwner() {
    if (!ownerTransferTarget.trim()) {
      return;
    }
    setSessionPending(true);
    setSessionError(null);
    try {
      const payload: SessionOwnerTransferRequest = { targetViewerSessionId: ownerTransferTarget };
      const response = await fetch(`${API_BASE}/session/owner-transfer`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        throw new Error(`Session owner transfer failed: ${response.status} ${response.statusText}`);
      }
      const identity = (await response.json()) as RelaySessionIdentityDescriptor;
      setRelaySessionIdentity(identity);
      setSessionOwnerName(identity.owner?.displayName ?? sessionOwnerName);
      setOffsets((previous) => ({ ...previous }));
    } catch (transferError) {
      setSessionError(transferError instanceof Error ? transferError.message : "Session owner transfer failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function addSessionDebugNote() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const payload: SessionDebugNoteRequest = {
        author: sessionOwnerName,
        message: sessionNoteDraft,
        artifactType: data.session.items[0]?.focusedArtifactType ?? null,
        artifactId: data.session.items[0]?.focusedArtifactId ?? null,
      };
      const response = await fetch(`${API_BASE}/session/notes`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        throw new Error(`Session note failed: ${response.status} ${response.statusText}`);
      }
      setSessionNoteDraft("");
      setOffsets((previous) => ({ ...previous }));
    } catch (noteError) {
      setSessionError(noteError instanceof Error ? noteError.message : "Session note failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function startSessionRecording() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const payload: SessionRecordingRequest = { actor: sessionOwnerName };
      const response = await fetch(`${API_BASE}/session/recording/start`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`Session recording start failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (recordingError) {
      setSessionError(recordingError instanceof Error ? recordingError.message : "Session recording start failed.");
    } finally {
      setSessionPending(false);
    }
  }

  async function stopSessionRecording() {
    setSessionPending(true);
    setSessionError(null);
    try {
      const payload: SessionRecordingRequest = { actor: sessionOwnerName };
      const response = await fetch(`${API_BASE}/session/recording/stop`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(`Session recording stop failed: ${response.status} ${response.statusText}`);
      }

      setOffsets((previous) => ({ ...previous }));
    } catch (recordingError) {
      setSessionError(recordingError instanceof Error ? recordingError.message : "Session recording stop failed.");
    } finally {
      setSessionPending(false);
    }
  }

  return (
    <div className="min-h-screen bg-shell text-mist">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(107,227,255,0.18),transparent_32%),radial-gradient(circle_at_top_right,rgba(243,201,105,0.16),transparent_25%),linear-gradient(180deg,#07111f_0%,#081425_38%,#060b14_100%)]" />
      <div className="absolute inset-0 bg-grid bg-[size:42px_42px] opacity-25" />
      <OnboardingOverlay
        step={onboardingStep}
        hasRequests={data.requests.total > 0}
        onSkip={() => {
          markOnboardingSeen();
          setOnboardingStep(null);
        }}
        onNext={() => setOnboardingStep((previous) => (previous === null ? null : previous + 1))}
        onFinish={() => {
          markOnboardingSeen();
          setOnboardingStep(null);
          startTransition(() => setActiveTab("requests"));
        }}
        onJumpToTab={(tab) => startTransition(() => setActiveTab(tab))}
      />
      <main className="relative mx-auto flex min-h-screen w-full max-w-[1500px] flex-col px-4 pb-8 pt-6 sm:px-6 lg:px-10">
        <header className="overflow-hidden rounded-[30px] border border-line bg-[linear-gradient(135deg,rgba(11,24,43,0.94),rgba(9,17,31,0.82))] shadow-halo">
          <div className="grid gap-8 px-6 py-7 sm:px-8 lg:grid-cols-[1.4fr_0.9fr] lg:px-10">
            <div className="space-y-6">
              <div className="inline-flex items-center gap-3 border border-line px-3 py-1 text-[11px] uppercase tracking-[0.28em] text-accent/90">
                spring-devtools-ui
                <span className="h-1.5 w-1.5 rounded-full bg-accent" />
                zero-config dashboard
              </div>
              <div className="space-y-3">
                <p className="text-[clamp(2.6rem,8vw,5.4rem)] font-semibold uppercase leading-none tracking-[-0.05em] text-white">
                  Observe your Spring Boot app where it runs.
                </p>
                <p className="max-w-2xl text-sm leading-6 text-mist/78 sm:text-base">
                  Endpoint maps, live request traces, resolved configuration, and recent log lines are surfaced
                  directly from the app with no database and no manual wiring.
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-3 text-xs uppercase tracking-[0.22em] text-mist/65">
                <span className="border border-line px-3 py-2">/_dev</span>
                <span className="border border-line px-3 py-2">localhost only</span>
                <span className="border border-line px-3 py-2">prod profile disabled</span>
              </div>
            </div>
            <div className="flex flex-col justify-between gap-5 border-l-0 border-line/70 lg:border-l lg:pl-8">
              <div className="space-y-2">
                <div className="text-xs uppercase tracking-[0.28em] text-gold/80">Runtime</div>
                <div className="text-4xl font-semibold tracking-[-0.05em] text-white">{data[activeTab].total}</div>
                <div className="text-sm text-mist/72">{labelForCount(activeTab)}</div>
              </div>
              <div className="space-y-4">
                <div className="flex items-center justify-between text-xs uppercase tracking-[0.22em] text-mist/60">
                  <span>status</span>
                  <span className={statusBadgeClass(status)}>{status}</span>
                </div>
                <div className="text-xs uppercase tracking-[0.22em] text-mist/60">
                  last sync {lastUpdated || "pending"}
                </div>
                {error ? <div className="text-sm text-ember">{error}</div> : null}
              </div>
            </div>
          </div>
        </header>

        <section className="mt-6 flex min-h-0 flex-1 flex-col overflow-hidden rounded-[28px] border border-line bg-[linear-gradient(180deg,rgba(9,17,31,0.92),rgba(7,12,22,0.96))]">
          <div className="flex flex-col gap-4 border-b border-line px-5 py-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="flex flex-wrap gap-2">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  className={[
                    "min-w-[132px] border px-4 py-3 text-left transition",
                    activeTab === tab.id
                      ? "border-accent bg-accent/10 text-white"
                      : "border-line text-mist/70 hover:border-accent/50 hover:text-white",
                  ].join(" ")}
                >
                  <div className="text-[11px] uppercase tracking-[0.28em]">{tab.label}</div>
                  <div className="mt-2 text-xs leading-5 text-mist/65">{tab.blurb}</div>
                </button>
              ))}
            </div>
            <div className="flex w-full max-w-md items-center gap-3 border border-line px-3 py-3 lg:w-[340px]">
              <span className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Filter</span>
              <input
                value={search}
                onChange={(event) => {
                  const nextValue = event.target.value;
                  setSearch(nextValue);
                  setOffsets(EMPTY_OFFSETS);
                }}
                placeholder="Search current stream"
                className="w-full bg-transparent text-sm text-white outline-none placeholder:text-mist/30"
              />
            </div>
          </div>
          {activeTab === "logs" ? (
            <div className="flex flex-col gap-3 border-b border-line px-5 py-4 sm:flex-row sm:items-center">
              <label className="flex items-center gap-3 border border-line px-3 py-3 text-[11px] uppercase tracking-[0.28em] text-mist/45">
                Level
                <select
                  value={logFilters.level}
                  onChange={(event) => {
                    const nextLevel = event.target.value;
                    setLogFilters((previous) => ({ ...previous, level: nextLevel }));
                    setOffsets((previous) => ({ ...previous, logs: 0 }));
                  }}
                  className="bg-transparent text-sm tracking-normal text-white outline-none"
                >
                  <option value="">All</option>
                  <option value="ERROR">ERROR</option>
                  <option value="WARN">WARN</option>
                  <option value="INFO">INFO</option>
                  <option value="DEBUG">DEBUG</option>
                  <option value="TRACE">TRACE</option>
                </select>
              </label>
              <label className="flex w-full items-center gap-3 border border-line px-3 py-3 text-[11px] uppercase tracking-[0.28em] text-mist/45">
                Logger
                <input
                  value={logFilters.logger}
                  onChange={(event) => {
                    const nextLogger = event.target.value;
                    setLogFilters((previous) => ({ ...previous, logger: nextLogger }));
                    setOffsets((previous) => ({ ...previous, logs: 0 }));
                  }}
                  placeholder="Filter by logger name"
                  className="w-full bg-transparent text-sm tracking-normal text-white outline-none placeholder:text-mist/30"
                />
              </label>
            </div>
          ) : null}

          <div className="grid min-h-0 flex-1 lg:grid-cols-[minmax(320px,0.95fr)_minmax(0,1.45fr)]">
            <aside className="min-h-0 border-b border-line lg:border-b-0 lg:border-r">
              <div className="flex items-center justify-between border-b border-line px-5 py-3 text-[11px] uppercase tracking-[0.28em] text-mist/55">
                <span>{tabTitle(activeTab)}</span>
                <span>{data[activeTab].total} total</span>
              </div>
              <div className="max-h-[40vh] overflow-auto lg:h-full lg:max-h-none">
                {renderList(activeTab, currentItems, activeKey, (nextKey) =>
                  setSelections((previous) => ({ ...previous, [activeTab]: nextKey })),
                )}
              </div>
              <div className="flex items-center justify-between border-t border-line px-5 py-3 text-xs text-mist/55">
                <span>
                  {activePage.total === 0 ? "0 results" : `${activePageStart}-${activePageEnd} of ${activePage.total}`}
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={!canGoPrevious}
                    onClick={() =>
                      setOffsets((previous) => ({
                        ...previous,
                        [activeTab]: Math.max(0, previous[activeTab] - activePage.limit),
                      }))
                    }
                    className="border border-line px-3 py-1 text-white disabled:cursor-not-allowed disabled:opacity-30"
                  >
                    Prev
                  </button>
                  <button
                    type="button"
                    disabled={!canGoNext}
                    onClick={() =>
                      setOffsets((previous) => ({
                        ...previous,
                        [activeTab]: previous[activeTab] + activePage.limit,
                      }))
                    }
                    className="border border-line px-3 py-1 text-white disabled:cursor-not-allowed disabled:opacity-30"
                  >
                    Next
                  </button>
                </div>
              </div>
            </aside>
            <section className="min-h-0 overflow-auto px-5 py-5">
              {activeTab === "webhooks" ? (
                <WebhookSimulatorPanel
                  targets={currentItems.webhooks}
                  method={apiTestMethod}
                  onMethodChange={setApiTestMethod}
                  selectedPath={webhookPath}
                  onSelectedPathChange={setWebhookPath}
                  body={webhookBody}
                  onBodyChange={setWebhookBody}
                  headers={webhookHeaders}
                  onHeadersChange={setWebhookHeaders}
                  sending={webhookSending}
                  error={webhookError}
                  result={webhookResult}
                  onSend={sendWebhook}
                />
              ) : activeTab === "fakeServices" ? (
                <FakeServicesPanel
                  service={activeItem as FakeExternalServiceDescriptor | null}
                  pendingId={fakeServicePendingId}
                  error={fakeServiceError}
                  onToggle={updateFakeService}
                  selectedRouteId={fakeServiceMockRouteId}
                  onSelectedRouteIdChange={setFakeServiceMockRouteId}
                  mockStatus={fakeServiceMockStatus}
                  onMockStatusChange={setFakeServiceMockStatus}
                  mockContentType={fakeServiceMockContentType}
                  onMockContentTypeChange={setFakeServiceMockContentType}
                  mockBody={fakeServiceMockBody}
                  onMockBodyChange={setFakeServiceMockBody}
                  onSaveMock={updateFakeServiceMock}
                />
              ) : activeTab === "time" ? (
                <TimeTravelPanel
                  state={activeItem as TimeTravelStateDescriptor | null}
                  pending={timePending}
                  error={timeError}
                  instant={timeInput}
                  zoneId={timeZoneInput}
                  reason={timeReasonInput}
                  durationMinutes={timeDurationInput}
                  onInstantChange={setTimeInput}
                  onZoneChange={setTimeZoneInput}
                  onReasonChange={setTimeReasonInput}
                  onDurationMinutesChange={setTimeDurationInput}
                  onApply={updateTime}
                  onReset={resetTime}
                />
              ) : activeTab === "session" ? (
                <SessionPanel
                  session={activeItem as RemoteSessionDescriptor | null}
                  ownerName={sessionOwnerName}
                  allowGuests={sessionAllowGuests}
                  shareRole={sessionShareRole}
                  validateToken={sessionValidateToken}
                  shareResult={sessionShareResult}
                  validationResult={sessionValidationResult}
                  pending={sessionPending}
                  error={sessionError}
                  hostedSessionView={hostedSessionView}
                  hostedSessionHistory={hostedSessionHistory}
                  relayViewerSessions={relayViewerSessions}
                  relaySessionIdentity={relaySessionIdentity}
                  ownerTransferTarget={ownerTransferTarget}
                  hostedViewerMemberId={hostedViewerMemberId}
                  hostedViewerRole={hostedViewerRole}
                  hostedViewerSource={hostedViewerSource}
                  onOwnerNameChange={setSessionOwnerName}
                  onAllowGuestsChange={setSessionAllowGuests}
                  onShareRoleChange={setSessionShareRole}
                  onValidateTokenChange={setSessionValidateToken}
                  onHostedViewerMemberIdChange={setHostedViewerMemberId}
                  onHostedViewerRoleChange={setHostedViewerRole}
                  onHostedViewerSourceChange={setHostedViewerSource}
                  onOwnerTransferTargetChange={setOwnerTransferTarget}
                  onAttach={attachSession}
                  onOpenTunnel={openSessionTunnel}
                  onCloseTunnel={closeSessionTunnel}
                  onHeartbeat={heartbeatSession}
                  onSync={syncSession}
                  onStartRecording={startSessionRecording}
                  onStopRecording={stopSessionRecording}
                  onShare={issueSessionShareToken}
                  onValidate={validateSessionShareToken}
                  sessionArtifact={sessionArtifact}
                  sessionNoteDraft={sessionNoteDraft}
                  onInspectRequestArtifact={inspectSessionRequestArtifact}
                  onSessionNoteDraftChange={setSessionNoteDraft}
                  onAddDebugNote={addSessionDebugNote}
                  onAddHostedViewer={addHostedViewerMember}
                  onRemoveHostedViewer={removeHostedViewerMember}
                  onRevokeRelayViewerSession={revokeRelayViewerSession}
                  onTransferOwner={transferSessionOwner}
                  onRotate={rotateSessionToken}
                  onRevoke={revokeSession}
                />
              ) : activeTab === "featureFlags" ? (
                <FeatureFlagsPanel
                  flag={activeItem as FeatureFlagDescriptor | null}
                  pendingKey={featureFlagPendingKey}
                  error={featureFlagError}
                  definitionDraft={featureFlagDefinitionDraft}
                  onDefinitionDraftChange={setFeatureFlagDefinitionDraft}
                  onToggle={updateFeatureFlag}
                  onReset={clearFeatureFlag}
                  onSaveDefinition={saveFeatureFlagDefinition}
                  onDeleteDefinition={deleteFeatureFlagDefinition}
                />
              ) : activeTab === "requests" ? (
                <RequestDetailPanel
                  request={activeItem as CapturedRequest | null}
                  replayPending={errorReplayPending}
                  replayError={errorReplayError}
                  replayResult={errorReplayResult}
                  onReplay={replayErrorRequest}
                />
              ) : activeTab === "config" ? (
                <ConfigPanel
                  property={activeItem as ConfigPropertyDescriptor | null}
                  snapshots={configSnapshots}
                  snapshotLabel={configSnapshotLabel}
                  onSnapshotLabelChange={setConfigSnapshotLabel}
                  selectedSnapshotId={selectedConfigSnapshotId}
                  onSelectedSnapshotIdChange={setSelectedConfigSnapshotId}
                  comparison={configComparison}
                  drift={configDrift}
                  pending={configPending}
                  error={configError}
                  onCreateSnapshot={createConfigSnapshot}
                  onCompareSnapshot={compareConfigSnapshot}
                  onDetectDrift={detectConfigDrift}
                />
              ) : (
                renderDetail(activeTab, activeItem)
              )}
            </section>
          </div>
        </section>
      </main>
    </div>
  );
}

function OnboardingOverlay({
  step,
  hasRequests,
  onSkip,
  onNext,
  onFinish,
  onJumpToTab,
}: {
  step: number | null;
  hasRequests: boolean;
  onSkip: () => void;
  onNext: () => void;
  onFinish: () => void;
  onJumpToTab: (tab: TabId) => void;
}) {
  if (step === null) return null;

  const title = step === 0 ? "Falkenr is running" : step === 1 ? "Quick tour" : "First value";
  const body = step === 0
    ? (
      <>
        <p className="text-sm leading-6 text-mist/80">
          You’re looking at your app’s live runtime. Everything here is happening inside your Spring Boot app with no setup required.
        </p>
      </>
    )
    : step === 1
      ? (
        <div className="space-y-3 text-sm leading-6 text-mist/80">
          <div>
            <div className="text-xs uppercase tracking-[0.22em] text-mist/60">1. Requests</div>
            <div>Every request hitting your app appears here. Click one to inspect headers, body, and response.</div>
          </div>
          <div>
            <div className="text-xs uppercase tracking-[0.22em] text-mist/60">2. Logs</div>
            <div>Logs stream in real time. Filter by level or logger.</div>
          </div>
          <div>
            <div className="text-xs uppercase tracking-[0.22em] text-mist/60">3. Config</div>
            <div>See your runtime config and property sources.</div>
          </div>
        </div>
      )
      : (
        <div className="space-y-3 text-sm leading-6 text-mist/80">
          {hasRequests ? (
            <p>
              You already have requests captured. Open the Requests tab and click the latest one to inspect the full exchange.
            </p>
          ) : (
            <p>
              No requests yet. Hit any endpoint in another tab and come back. Falkenr will capture it instantly.
            </p>
          )}
          <div className="border border-line/70 bg-black/20 p-3 font-mono text-xs text-mist/80">
            curl http://localhost:8080/your-endpoint
          </div>
        </div>
      );

  const primaryLabel = step === 0 ? "Show me around" : step === 1 ? "Got it" : "Open Requests";
  const secondaryLabel = step === 0 ? "Skip" : step === 1 ? "Skip" : "Done";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/55" />
      <div className="relative w-full max-w-[560px] overflow-hidden rounded-[18px] border border-line bg-[linear-gradient(180deg,rgba(9,17,31,0.96),rgba(7,12,22,0.98))] p-6 shadow-halo">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/80">Onboarding</div>
        <div className="mt-3 text-2xl font-semibold tracking-[-0.03em] text-white">{title}</div>
        <div className="mt-4">{body}</div>
        <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-end">
          <button
            type="button"
            onClick={() => {
              if (step === 2) {
                onFinish();
              } else {
                onSkip();
              }
            }}
            className="border border-line px-5 py-3 text-xs uppercase tracking-[0.22em] text-mist/80 transition hover:border-accent/50 hover:text-white"
          >
            {secondaryLabel}
          </button>
          <button
            type="button"
            onClick={() => {
              if (step === 2) {
                onJumpToTab("requests");
                onFinish();
                return;
              }
              if (step === 1) {
                onJumpToTab("requests");
                onFinish();
                return;
              }
              onNext();
            }}
            className="border border-line bg-accent/20 px-5 py-3 text-xs uppercase tracking-[0.22em] text-white transition hover:border-accent"
          >
            {primaryLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

async function readJson<T>(suffix: string, params?: Record<string, string | number>): Promise<T> {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== "") {
      searchParams.set(key, String(value));
    }
  }

  const response = await fetch(`${API_BASE}${suffix}?${searchParams.toString()}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Request failed for ${suffix}: ${response.status} ${response.statusText}`);
  }

  return (await response.json()) as T;
}

function ensureSelections(previous: SelectionState, nextData: TabData): SelectionState {
  return {
    endpoints: coerceSelection(previous.endpoints, nextData.endpoints.items.map(endpointKey)),
    requests: coerceSelection(previous.requests, nextData.requests.items.map(requestKey)),
    config: coerceSelection(previous.config, nextData.config.items.map((item) => item.key)),
    featureFlags: coerceSelection(previous.featureFlags, nextData.featureFlags.items.map(featureFlagKey)),
    dependencies: coerceSelection(previous.dependencies, nextData.dependencies.items.map(dependencyNodeKey)),
    time: coerceSelection(previous.time, nextData.time.items.map(timeStateKey)),
    session: coerceSelection(previous.session, nextData.session.items.map(sessionKey)),
    logs: coerceSelection(previous.logs, nextData.logs.items.map(logKey)),
    jobs: coerceSelection(previous.jobs, nextData.jobs.items.map(jobKey)),
    dbQueries: coerceSelection(previous.dbQueries, nextData.dbQueries.items.map(dbQueryKey)),
    webhooks: coerceSelection(previous.webhooks, nextData.webhooks.items.map(webhookKey)),
    fakeServices: coerceSelection(previous.fakeServices, nextData.fakeServices.items.map(fakeServiceKey)),
    auditLogs: coerceSelection(previous.auditLogs, nextData.auditLogs.items.map((item) => item.auditId)),
  };
}

function coerceSelection(current: string | null, availableKeys: string[]): string | null {
  if (current && availableKeys.includes(current)) {
    return current;
  }
  return availableKeys[0] ?? null;
}

function endpointKey(item: EndpointDescriptor): string {
  return `${item.method}:${item.path}:${item.controller}:${item.methodName}`;
}

function requestKey(item: CapturedRequest): string {
  return item.requestId;
}

function featureFlagKey(item: FeatureFlagDescriptor): string {
  return item.key;
}

function dependencyNodeKey(item: DependencyNodeDescriptor): string {
  return item.beanName;
}

function logKey(item: LogEventDescriptor): string {
  return `${item.timestamp}:${item.level}:${item.logger}`;
}

function auditLogKey(item: AuditLogEventDescriptor): string {
  return item.auditId;
}

function jobKey(item: JobDescriptor): string {
  return `${item.beanName}:${item.methodName}:${item.triggerType}:${item.expression}`;
}

function dbQueryKey(item: DbQueryDescriptor): string {
  return `${item.timestamp}:${item.statementType}:${item.sql}`;
}

function webhookKey(item: WebhookTargetDescriptor): string {
  return `${item.method}:${item.path}:${item.controller}:${item.methodName}`;
}

function fakeServiceKey(item: FakeExternalServiceDescriptor): string {
  return item.serviceId;
}

function timeStateKey(item: TimeTravelStateDescriptor): string {
  return `${item.currentTime}:${item.zoneId}:${item.overridden}`;
}

function sessionKey(item: RemoteSessionDescriptor): string {
  return item.sessionId;
}

function resolveActiveItem(
  tab: TabId,
  items: {
    endpoints: EndpointDescriptor[];
    requests: CapturedRequest[];
    config: ConfigPropertyDescriptor[];
    featureFlags: FeatureFlagDescriptor[];
    dependencies: DependencyNodeDescriptor[];
    time: TimeTravelStateDescriptor[];
    session: RemoteSessionDescriptor[];
    logs: LogEventDescriptor[];
    jobs: JobDescriptor[];
    dbQueries: DbQueryDescriptor[];
    webhooks: WebhookTargetDescriptor[];
    fakeServices: FakeExternalServiceDescriptor[];
    auditLogs: AuditLogEventDescriptor[];
  },
  activeKey: string | null,
) {
  switch (tab) {
    case "endpoints":
      return resolveFromList(items.endpoints, activeKey, endpointKey);
    case "requests":
      return resolveFromList(items.requests, activeKey, requestKey);
    case "config":
      return resolveFromList(items.config, activeKey, (item) => item.key);
    case "featureFlags":
      return resolveFromList(items.featureFlags, activeKey, featureFlagKey);
    case "dependencies":
      return resolveFromList(items.dependencies, activeKey, dependencyNodeKey);
    case "time":
      return resolveFromList(items.time, activeKey, timeStateKey);
    case "session":
      return resolveFromList(items.session, activeKey, sessionKey);
    case "logs":
      return resolveFromList(items.logs, activeKey, logKey);
    case "jobs":
      return resolveFromList(items.jobs, activeKey, jobKey);
    case "dbQueries":
      return resolveFromList(items.dbQueries, activeKey, dbQueryKey);
    case "webhooks":
      return resolveFromList(items.webhooks, activeKey, webhookKey);
    case "fakeServices":
      return resolveFromList(items.fakeServices, activeKey, fakeServiceKey);
    case "auditLogs":
      return resolveFromList(items.auditLogs, activeKey, auditLogKey);
  }
}

function resolveFromList<T>(items: T[], activeKey: string | null, getKey: (item: T) => string): T | null {
  if (items.length === 0) {
    return null;
  }
  if (!activeKey) {
    return items[0];
  }
  return items.find((item) => getKey(item) === activeKey) ?? items[0];
}

function renderList(
  tab: TabId,
  items: {
    endpoints: EndpointDescriptor[];
    requests: CapturedRequest[];
    config: ConfigPropertyDescriptor[];
    featureFlags: FeatureFlagDescriptor[];
    dependencies: DependencyNodeDescriptor[];
    time: TimeTravelStateDescriptor[];
    session: RemoteSessionDescriptor[];
    logs: LogEventDescriptor[];
    jobs: JobDescriptor[];
    dbQueries: DbQueryDescriptor[];
    webhooks: WebhookTargetDescriptor[];
    fakeServices: FakeExternalServiceDescriptor[];
    auditLogs: AuditLogEventDescriptor[];
  },
  activeKey: string | null,
  onSelect: (key: string) => void,
) {
  if (items[tab].length === 0) {
    return <div className="px-5 py-8 text-sm text-mist/55">No records match the current filter.</div>;
  }

  if (tab === "endpoints") {
    return items.endpoints.map((item) => {
      const isActive = activeKey !== null && endpointKey(item) === activeKey;
      return (
        <button key={endpointKey(item)} type="button" onClick={() => onSelect(endpointKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-[0.24em] text-accent/85">{item.method}</span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.controller}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.path}</div>
          <div className="mt-1 text-xs text-mist/55">{item.methodName}()</div>
        </button>
      );
    });
  }

  if (tab === "requests") {
    return items.requests.map((item) => {
      const isActive = activeKey !== null && requestKey(item) === activeKey;
      return (
        <button key={requestKey(item)} type="button" onClick={() => onSelect(requestKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-[0.24em] text-gold/85">{item.method}</span>
            <span className="text-xs text-mist/45">{formatTimestamp(item.timestamp)}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.path}</div>
          <div className="mt-1 text-xs text-mist/55">status {item.responseStatus}</div>
        </button>
      );
    });
  }

  if (tab === "config") {
    return items.config.map((item) => {
      const isActive = activeKey !== null && item.key === activeKey;
      return (
        <button key={item.key} type="button" onClick={() => onSelect(item.key)} className={rowClass(isActive)}>
          <div className="text-sm text-white">{item.key}</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.propertySource}</div>
        </button>
      );
    });
  }

  if (tab === "featureFlags") {
    return items.featureFlags.map((item) => {
      const isActive = activeKey !== null && featureFlagKey(item) === activeKey;
      return (
        <button key={featureFlagKey(item)} type="button" onClick={() => onSelect(featureFlagKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className={item.enabled ? "text-xs uppercase tracking-[0.24em] text-accent/85" : "text-xs uppercase tracking-[0.24em] text-mist/55"}>
              {item.enabled ? "enabled" : "disabled"}
            </span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.overridden ? "override" : "env"}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.key}</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.propertySource}</div>
        </button>
      );
    });
  }

  if (tab === "dependencies") {
    return items.dependencies.map((item) => {
      const isActive = activeKey !== null && dependencyNodeKey(item) === activeKey;
      return (
        <button key={dependencyNodeKey(item)} type="button" onClick={() => onSelect(dependencyNodeKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-[0.24em] text-accent/85">{item.scope}</span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.dependencies.length} deps</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.beanName}</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.beanType}</div>
        </button>
      );
    });
  }

  if (tab === "time") {
    return items.time.map((item) => {
      const isActive = activeKey !== null && timeStateKey(item) === activeKey;
      return (
        <button key={timeStateKey(item)} type="button" onClick={() => onSelect(timeStateKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className={item.overridden ? "text-xs uppercase tracking-[0.24em] text-accent/85" : "text-xs uppercase tracking-[0.24em] text-mist/55"}>
              {item.overridden ? "overridden" : "system"}
            </span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.zoneId}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.currentTime}</div>
          <div className="mt-1 truncate text-xs text-mist/55">Injected application clock</div>
        </button>
      );
    });
  }

  if (tab === "session") {
    return items.session.map((item) => {
      const isActive = activeKey !== null && sessionKey(item) === activeKey;
      return (
        <button key={sessionKey(item)} type="button" onClick={() => onSelect(sessionKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className={item.attached ? "text-xs uppercase tracking-[0.24em] text-accent/85" : "text-xs uppercase tracking-[0.24em] text-mist/55"}>
              {item.attached ? "attached" : "idle"}
            </span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.accessScope}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.ownerName}</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.shareUrl}</div>
        </button>
      );
    });
  }

  if (tab === "jobs") {
    return items.jobs.map((item) => {
      const isActive = activeKey !== null && jobKey(item) === activeKey;
      return (
        <button key={jobKey(item)} type="button" onClick={() => onSelect(jobKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-[0.24em] text-accent/85">{item.triggerType}</span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.beanType}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.methodName}()</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.expression || "scheduler metadata unavailable"}</div>
        </button>
      );
    });
  }

  if (tab === "dbQueries") {
    return items.dbQueries.map((item) => {
      const isActive = activeKey !== null && dbQueryKey(item) === activeKey;
      return (
        <button key={dbQueryKey(item)} type="button" onClick={() => onSelect(dbQueryKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-[0.24em] text-gold/85">{item.statementType}</span>
            <span className="text-xs text-mist/45">{formatTimestamp(item.timestamp)}</span>
          </div>
          <div className="mt-2 overflow-hidden text-ellipsis whitespace-nowrap text-sm text-white">{item.sql}</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.dataSource}</div>
        </button>
      );
    });
  }

  if (tab === "webhooks") {
    return items.webhooks.map((item) => {
      const isActive = activeKey !== null && webhookKey(item) === activeKey;
      return (
        <button key={webhookKey(item)} type="button" onClick={() => onSelect(webhookKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-[0.24em] text-ember">{item.method}</span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.controller}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.path}</div>
          <div className="mt-1 text-xs text-mist/55">{item.methodName}()</div>
        </button>
      );
    });
  }

  if (tab === "fakeServices") {
    return items.fakeServices.map((item) => {
      const isActive = activeKey !== null && fakeServiceKey(item) === activeKey;
      return (
        <button key={fakeServiceKey(item)} type="button" onClick={() => onSelect(fakeServiceKey(item))} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className={item.enabled ? "text-xs uppercase tracking-[0.24em] text-accent/85" : "text-xs uppercase tracking-[0.24em] text-mist/55"}>
              {item.enabled ? "enabled" : "disabled"}
            </span>
            <span className="text-[11px] uppercase tracking-[0.2em] text-mist/40">{item.routes.length} routes</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.displayName}</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.basePath}</div>
        </button>
      );
    });
  }

  if (tab === "auditLogs") {
    return items.auditLogs.map((item) => {
      const isActive = activeKey !== null && item.auditId === activeKey;
      return (
        <button key={item.auditId} type="button" onClick={() => onSelect(item.auditId)} className={rowClass(isActive)}>
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-[0.24em] text-ember">{item.category}</span>
            <span className="text-xs text-mist/45">{formatTimestamp(item.timestamp)}</span>
          </div>
          <div className="mt-2 text-sm text-white">{item.action}</div>
          <div className="mt-1 truncate text-xs text-mist/55">{item.actor}</div>
        </button>
      );
    });
  }

  return items.logs.map((item) => {
    const isActive = activeKey !== null && logKey(item) === activeKey;
    return (
      <button key={logKey(item)} type="button" onClick={() => onSelect(logKey(item))} className={rowClass(isActive)}>
        <div className="flex items-center justify-between gap-3">
          <span className={levelClass(item.level)}>{item.level}</span>
          <span className="text-xs text-mist/45">{formatTimestamp(item.timestamp)}</span>
        </div>
        <div className="mt-2 overflow-hidden text-ellipsis whitespace-nowrap text-sm text-white">{item.message}</div>
        <div className="mt-1 truncate text-xs text-mist/55">{item.logger}</div>
      </button>
    );
  });
}

function renderDetail(
  tab: TabId,
  item: EndpointDescriptor | CapturedRequest | ConfigPropertyDescriptor | FeatureFlagDescriptor | DependencyNodeDescriptor | TimeTravelStateDescriptor | RemoteSessionDescriptor | LogEventDescriptor | JobDescriptor | DbQueryDescriptor | WebhookTargetDescriptor | FakeExternalServiceDescriptor | AuditLogEventDescriptor | null,
) {
  if (!item) {
    return (
      <div className="flex h-full min-h-[320px] items-center justify-center border border-dashed border-line text-sm text-mist/55">
        Select an item to inspect details.
      </div>
    );
  }

  if (tab === "endpoints") {
    const endpoint = item as EndpointDescriptor;
    return (
      <DetailLayout title={endpoint.path} eyebrow={endpoint.method}>
        <DetailRow label="Controller" value={endpoint.controller} />
        <DetailRow label="Handler method" value={`${endpoint.methodName}()`} />
        <CodeBlock value={JSON.stringify(endpoint, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "config") {
    const config = item as ConfigPropertyDescriptor;
    return (
      <DetailLayout title={config.key} eyebrow="Resolved property">
        <DetailRow label="Value" value={config.value || "Empty"} />
        <DetailRow label="Property source" value={config.propertySource} />
        <CodeBlock value={JSON.stringify(config, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "featureFlags") {
    const flag = item as FeatureFlagDescriptor;
    return (
      <DetailLayout title={flag.key} eyebrow={flag.enabled ? "Feature enabled" : "Feature disabled"}>
        <DetailRow label="Current state" value={flag.enabled ? "Enabled" : "Disabled"} />
        <DetailRow label="Property source" value={flag.propertySource} />
        <DetailRow label="Override status" value={flag.overridden ? "Overridden locally in /_dev" : "Resolved from app environment"} />
        <CodeBlock value={JSON.stringify(flag, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "dependencies") {
    const node = item as DependencyNodeDescriptor;
    return (
      <DetailLayout title={node.beanName} eyebrow="Dependency graph">
        <DetailRow label="Bean type" value={node.beanType} />
        <DetailRow label="Scope" value={node.scope} />
        <CodeBlock value={node.dependencies.length === 0 ? "[]" : JSON.stringify(node.dependencies, null, 2)} label="Dependencies" />
        <CodeBlock value={node.dependents.length === 0 ? "[]" : JSON.stringify(node.dependents, null, 2)} label="Dependents" />
        <CodeBlock value={JSON.stringify(node, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "time") {
    const state = item as TimeTravelStateDescriptor;
    return (
      <DetailLayout title={state.currentTime} eyebrow={state.overridden ? "Time override active" : "System clock active"}>
        <DetailRow label="Zone" value={state.zoneId} />
        <DetailRow label="Override status" value={state.overridden ? "Injected clock overridden locally" : "Using system time"} />
        <CodeBlock value={JSON.stringify(state, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "session") {
    const session = item as RemoteSessionDescriptor;
    return (
      <DetailLayout title={session.ownerName} eyebrow={session.attached ? "Remote attach ready" : "Local session idle"}>
        <DetailRow label="Session id" value={session.sessionId} />
        <DetailRow label="Relay status" value={session.relayStatus} />
        <DetailRow label="Access scope" value={session.accessScope} />
        <DetailRow label="Allowed roles" value={session.allowedRoles.join(", ")} />
        <DetailRow label="Share URL" value={session.shareUrl} />
        <DetailRow label="Relay URL" value={session.relayUrl} />
        <CodeBlock value={JSON.stringify(session, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "jobs") {
    const job = item as JobDescriptor;
    return (
      <DetailLayout title={`${job.beanType}.${job.methodName}()`} eyebrow="Scheduled job">
        <DetailRow label="Bean name" value={job.beanName} />
        <DetailRow label="Trigger type" value={job.triggerType} />
        <DetailRow label="Expression" value={job.expression || "Not declared"} />
        <DetailRow label="Scheduler" value={job.scheduler || "Default scheduler"} />
        <CodeBlock value={JSON.stringify(job, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "dbQueries") {
    const query = item as DbQueryDescriptor;
    return (
      <DetailLayout title={query.statementType.toUpperCase()} eyebrow="Database query">
        <DetailRow label="Timestamp" value={formatTimestamp(query.timestamp)} />
        <DetailRow label="Datasource" value={query.dataSource} />
        <DetailRow label="Rows affected" value={query.rowsAffected == null ? "Unknown" : String(query.rowsAffected)} />
        <CodeBlock value={query.sql} label="SQL" />
        <CodeBlock value={JSON.stringify(query, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "webhooks") {
    const webhook = item as WebhookTargetDescriptor;
    return (
      <DetailLayout title={webhook.path} eyebrow={webhook.method}>
        <DetailRow label="Controller" value={webhook.controller} />
        <DetailRow label="Handler method" value={`${webhook.methodName}()`} />
        <CodeBlock value={JSON.stringify(webhook, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "fakeServices") {
    const service = item as FakeExternalServiceDescriptor;
    return (
      <DetailLayout title={service.displayName} eyebrow={service.enabled ? "Stub enabled" : "Stub disabled"}>
        <DetailRow label="Service id" value={service.serviceId} />
        <DetailRow label="Base path" value={service.basePath} />
        <DetailRow label="Description" value={service.description} />
        <CodeBlock value={JSON.stringify(service.routes, null, 2)} label="Routes" />
        <CodeBlock value={JSON.stringify(service, null, 2)} />
      </DetailLayout>
    );
  }

  if (tab === "auditLogs") {
    const event = item as AuditLogEventDescriptor;
    return (
      <DetailLayout title={event.action} eyebrow={event.category}>
        <DetailRow label="Actor" value={event.actor} />
        <DetailRow label="Timestamp" value={formatTimestamp(event.timestamp)} />
        <DetailRow label="Detail" value={event.detail || "No detail"} />
        <CodeBlock value={JSON.stringify(event, null, 2)} />
      </DetailLayout>
    );
  }

  const log = item as LogEventDescriptor;
  return (
    <DetailLayout title={log.logger} eyebrow={log.level}>
      <DetailRow label="Timestamp" value={formatTimestamp(log.timestamp)} />
      <DetailRow label="Message" value={log.message} />
      {log.stackTrace ? <CodeBlock value={log.stackTrace} label="Stack trace" /> : null}
      <CodeBlock value={JSON.stringify(log, null, 2)} />
    </DetailLayout>
  );
}

function RequestDetailPanel({
  request,
  replayPending,
  replayError,
  replayResult,
  onReplay,
}: {
  request: CapturedRequest | null;
  replayPending: boolean;
  replayError: string | null;
  replayResult: ErrorReplayResult | null;
  onReplay: (requestId: string) => Promise<void>;
}) {
  if (!request) {
    return (
      <div className="flex h-full min-h-[320px] items-center justify-center border border-dashed border-line text-sm text-mist/55">
        Select an item to inspect details.
      </div>
    );
  }

  const canReplay = request.responseStatus >= 500;
  const matchingReplay = replayResult?.requestId === request.requestId ? replayResult : null;

  return (
    <DetailLayout title={request.path} eyebrow={`${request.method}  ${request.responseStatus}`}>
      <DetailRow label="Request id" value={request.requestId} />
      <DetailRow label="Timestamp" value={formatTimestamp(request.timestamp)} />
      <DetailRow
        label="Capture flags"
        value={[
          request.binaryBody ? "binary body omitted" : "text body",
          request.bodyTruncated ? "preview truncated" : "full preview",
        ].join(" · ")}
      />
      <DetailRow label="Body" value={request.body || "Empty"} />
      <CodeBlock value={JSON.stringify(request.headers, null, 2)} label="Headers" />
      {canReplay ? (
        <div className="space-y-3 border border-line/70 bg-black/20 p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <div className="text-[11px] uppercase tracking-[0.28em] text-ember">Error replay</div>
              <div className="mt-2 text-sm text-white">Replay this captured 5xx request against the running local app.</div>
            </div>
            <button
              type="button"
              onClick={() => void onReplay(request.requestId)}
              disabled={replayPending}
              className="border border-ember/60 px-3 py-2 text-xs font-semibold uppercase tracking-[0.24em] text-ember transition hover:bg-ember/10 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {replayPending ? "Replaying..." : "Replay error"}
            </button>
          </div>
          {replayError ? <div className="text-sm text-ember">{replayError}</div> : null}
          {matchingReplay ? (
            <div className="space-y-3 border-t border-line/70 pt-3">
              <DetailRow label="Original status" value={String(matchingReplay.originalStatus)} />
              <DetailRow label="Replay status" value={String(matchingReplay.replayStatus)} />
              <DetailRow label="Replayed at" value={formatTimestamp(matchingReplay.replayedAt)} />
              <CodeBlock value={matchingReplay.responseBody || "Empty"} label="Replay response" />
            </div>
          ) : null}
        </div>
      ) : null}
    </DetailLayout>
  );
}

function DetailLayout({
  title,
  eyebrow,
  children,
}: {
  title: string;
  eyebrow: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-6">
      <div className="space-y-3 border-b border-line pb-5">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/80">{eyebrow}</div>
        <h2 className="text-2xl font-semibold tracking-[-0.04em] text-white sm:text-3xl">{title}</h2>
      </div>
      <div className="space-y-4">{children}</div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="border-b border-line/80 pb-4">
      <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">{label}</div>
      <div className="mt-2 break-words text-sm leading-6 text-white">{value}</div>
    </div>
  );
}

function CodeBlock({ value, label = "Payload" }: { value: string; label?: string }) {
  return (
    <div className="space-y-2">
      <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">{label}</div>
      <pre className="overflow-auto border border-line bg-black/20 p-4 text-xs leading-6 text-mist/84">
        <code>{value}</code>
      </pre>
    </div>
  );
}

function TeamStatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="border border-line/60 bg-black/20 p-4">
      <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">{label}</div>
      <div className="mt-3 text-2xl font-semibold tracking-[-0.04em] text-white">{value}</div>
    </div>
  );
}

function FeatureFlagsPanel({
  flag,
  pendingKey,
  error,
  definitionDraft,
  onDefinitionDraftChange,
  onToggle,
  onReset,
  onSaveDefinition,
  onDeleteDefinition,
}: {
  flag: FeatureFlagDescriptor | null;
  pendingKey: string | null;
  error: string | null;
  definitionDraft: {
    displayName: string;
    description: string;
    owner: string;
    tags: string;
    lifecycle: string;
    allowOverride: boolean;
  };
  onDefinitionDraftChange: (value: {
    displayName: string;
    description: string;
    owner: string;
    tags: string;
    lifecycle: string;
    allowOverride: boolean;
  }) => void;
  onToggle: (key: string, enabled: boolean) => void;
  onReset: (key: string) => void;
  onSaveDefinition: (key: string) => void;
  onDeleteDefinition: (key: string) => void;
}) {
  useEffect(() => {
    if (!flag) {
      return;
    }
    const definition = flag.definition;
    onDefinitionDraftChange({
      displayName: definition?.displayName ?? "",
      description: definition?.description ?? "",
      owner: definition?.owner ?? "",
      tags: definition?.tags?.join(", ") ?? "",
      lifecycle: definition?.lifecycle ?? "active",
      allowOverride: definition?.allowOverride ?? true,
    });
  }, [flag?.key, flag?.definition?.lastModifiedAt]);

  if (!flag) {
    return (
      <div className="flex h-full min-h-[320px] items-center justify-center border border-dashed border-line text-sm text-mist/55">
        Select a feature flag to inspect details.
      </div>
    );
  }

  const isPending = pendingKey === flag.key;
  const definition = flag.definition;

  return (
    <div className="space-y-6">
      <div className="space-y-3 border-b border-line pb-5">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/80">Feature flag system</div>
        <h2 className="text-2xl font-semibold tracking-[-0.04em] text-white sm:text-3xl">{flag.key}</h2>
        <p className="max-w-2xl text-sm leading-6 text-mist/70">
          Local overrides still work, but this panel can also save an auditable flag definition with owner, lifecycle, and override policy so environments stop treating flags as anonymous booleans.
        </p>
      </div>

      <DetailRow label="Current state" value={flag.enabled ? "Enabled" : "Disabled"} />
      <DetailRow label="Property source" value={flag.propertySource} />
      <DetailRow label="Override status" value={flag.overridden ? "Overridden locally in /_dev" : "Resolved from the app environment"} />
      <DetailRow label="Definition status" value={definition ? (definition.persisted ? "Saved definition" : "Transient definition") : "No saved definition"} />
      <DetailRow label="Owner" value={definition?.owner || "Unassigned"} />
      <DetailRow label="Lifecycle" value={definition?.lifecycle || "active"} />
      <DetailRow label="Override policy" value={definition?.allowOverride === false ? "Overrides blocked by policy" : "Overrides allowed"} />
      {definition?.tags?.length ? <DetailRow label="Tags" value={definition.tags.join(", ")} /> : null}
      {definition?.description ? <DetailRow label="Description" value={definition.description} /> : null}
      {definition?.lastModifiedAt ? <DetailRow label="Definition updated" value={`${formatTimestamp(definition.lastModifiedAt)} by ${definition.lastModifiedBy || "unknown"}`} /> : null}

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => onToggle(flag.key, !flag.enabled)}
          disabled={isPending || definition?.allowOverride === false}
          className="border border-accent px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isPending ? "Saving" : flag.enabled ? "Disable locally" : "Enable locally"}
        </button>
        <button
          type="button"
          onClick={() => onReset(flag.key)}
          disabled={isPending || !flag.overridden}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Reset override
        </button>
        {error ? <div className="text-sm text-ember">{error}</div> : null}
      </div>

      <div className="space-y-4 border border-line bg-panel/60 p-5">
        <div>
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Definition metadata</div>
          <div className="mt-2 text-sm text-mist/70">
            Persist the human context and policy for this flag so staged environments can see ownership and whether runtime overrides should be allowed.
          </div>
        </div>
        <div className="grid gap-4 xl:grid-cols-2">
          <label className="block space-y-2">
            <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Display name</div>
            <input
              value={definitionDraft.displayName}
              onChange={(event) => onDefinitionDraftChange({ ...definitionDraft, displayName: event.target.value })}
              className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
            />
          </label>
          <label className="block space-y-2">
            <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Owner</div>
            <input
              value={definitionDraft.owner}
              onChange={(event) => onDefinitionDraftChange({ ...definitionDraft, owner: event.target.value })}
              className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
            />
          </label>
          <label className="block space-y-2 xl:col-span-2">
            <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Description</div>
            <textarea
              value={definitionDraft.description}
              onChange={(event) => onDefinitionDraftChange({ ...definitionDraft, description: event.target.value })}
              rows={3}
              className="w-full border border-line bg-black/20 p-4 text-sm leading-6 text-white outline-none"
            />
          </label>
          <label className="block space-y-2">
            <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Tags</div>
            <input
              value={definitionDraft.tags}
              onChange={(event) => onDefinitionDraftChange({ ...definitionDraft, tags: event.target.value })}
              placeholder="checkout, revenue, staging"
              className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
            />
          </label>
          <label className="block space-y-2">
            <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Lifecycle</div>
            <select
              value={definitionDraft.lifecycle}
              onChange={(event) => onDefinitionDraftChange({ ...definitionDraft, lifecycle: event.target.value })}
              className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
            >
              <option value="active">active</option>
              <option value="experimental">experimental</option>
              <option value="deprecated">deprecated</option>
              <option value="ops">ops</option>
            </select>
          </label>
        </div>
        <label className="flex items-center gap-3 text-sm text-mist/75">
          <input
            type="checkbox"
            checked={definitionDraft.allowOverride}
            onChange={(event) => onDefinitionDraftChange({ ...definitionDraft, allowOverride: event.target.checked })}
            className="h-4 w-4 border border-line bg-black/20"
          />
          Allow runtime overrides from `/_dev`
        </label>
        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => onSaveDefinition(flag.key)}
            disabled={isPending}
            className="border border-accent px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
          >
            {isPending ? "Saving" : definition ? "Update definition" : "Save definition"}
          </button>
          <button
            type="button"
            onClick={() => onDeleteDefinition(flag.key)}
            disabled={isPending || !definition}
            className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
          >
            Delete definition
          </button>
        </div>
      </div>

      <CodeBlock value={JSON.stringify(flag, null, 2)} label="Resolved flag" />
    </div>
  );
}

function ConfigPanel({
  property,
  snapshots,
  snapshotLabel,
  onSnapshotLabelChange,
  selectedSnapshotId,
  onSelectedSnapshotIdChange,
  comparison,
  drift,
  pending,
  error,
  onCreateSnapshot,
  onCompareSnapshot,
  onDetectDrift,
}: {
  property: ConfigPropertyDescriptor | null;
  snapshots: ConfigSnapshotDescriptor[];
  snapshotLabel: string;
  onSnapshotLabelChange: (value: string) => void;
  selectedSnapshotId: string;
  onSelectedSnapshotIdChange: (value: string) => void;
  comparison: ConfigComparisonDescriptor | null;
  drift: ConfigDriftDescriptor | null;
  pending: boolean;
  error: string | null;
  onCreateSnapshot: () => void;
  onCompareSnapshot: (snapshotId: string) => void;
  onDetectDrift: (snapshotId: string) => void;
}) {
  const selectedSnapshot = snapshots.find((snapshot) => snapshot.snapshotId === selectedSnapshotId) ?? null;
  const comparisonEntries = comparison?.entries.filter((entry) => entry.status !== "UNCHANGED").slice(0, 12) ?? [];
  const driftEntries = drift?.entries.filter((entry) => entry.status !== "UNCHANGED").slice(0, 8) ?? [];

  return (
    <div className="space-y-6">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
        <div className="space-y-4 border border-line bg-panel/60 p-5">
          <div>
            <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Current property</div>
            <div className="mt-3 text-lg font-semibold text-white">{property?.key ?? "No property selected"}</div>
            <div className="mt-2 text-sm text-mist/70">{property?.propertySource ?? "Pick a config entry from the left to inspect it here."}</div>
          </div>
          <DetailRow label="Value" value={property?.value || "Empty"} />
          {property ? <CodeBlock value={JSON.stringify(property, null, 2)} /> : null}
        </div>
        <div className="space-y-4 border border-line bg-panel/60 p-5">
          <div className="text-[11px] uppercase tracking-[0.28em] text-gold/75">Environment comparison</div>
          <label className="block text-[11px] uppercase tracking-[0.24em] text-mist/50">
            Snapshot label
            <input
              aria-label="Config snapshot label"
              value={snapshotLabel}
              onChange={(event) => onSnapshotLabelChange(event.target.value)}
              className="mt-2 w-full border border-line bg-transparent px-3 py-3 text-sm normal-case tracking-normal text-white outline-none"
            />
          </label>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              disabled={pending}
              onClick={onCreateSnapshot}
              className="border border-accent px-4 py-2 text-xs uppercase tracking-[0.24em] text-white disabled:cursor-not-allowed disabled:opacity-40"
            >
              Save snapshot
            </button>
            <button
              type="button"
              disabled={pending || !selectedSnapshotId}
              onClick={() => onCompareSnapshot(selectedSnapshotId)}
              className="border border-line px-4 py-2 text-xs uppercase tracking-[0.24em] text-white disabled:cursor-not-allowed disabled:opacity-40"
            >
              Compare to current
            </button>
            <button
              type="button"
              disabled={pending || snapshots.length === 0}
              onClick={() => onDetectDrift(selectedSnapshotId)}
              className="border border-gold/60 px-4 py-2 text-xs uppercase tracking-[0.24em] text-white disabled:cursor-not-allowed disabled:opacity-40"
            >
              Detect drift
            </button>
          </div>
          <label className="block text-[11px] uppercase tracking-[0.24em] text-mist/50">
            Saved snapshot
            <select
              aria-label="Config snapshot selector"
              value={selectedSnapshotId}
              onChange={(event) => onSelectedSnapshotIdChange(event.target.value)}
              className="mt-2 w-full border border-line bg-shell px-3 py-3 text-sm normal-case tracking-normal text-white outline-none"
            >
              <option value="">Select a snapshot</option>
              {snapshots.map((snapshot) => (
                <option key={snapshot.snapshotId} value={snapshot.snapshotId}>
                  {snapshot.label}
                </option>
              ))}
            </select>
          </label>
          {selectedSnapshot ? (
            <div className="text-sm text-mist/68">
              Saved {formatTimestamp(selectedSnapshot.capturedAt)} with {selectedSnapshot.properties.length} properties.
            </div>
          ) : (
            <div className="text-sm text-mist/50">No saved snapshots yet.</div>
          )}
          {error ? <div className="text-sm text-ember">{error}</div> : null}
        </div>
      </div>

      <div className="space-y-4 border border-line bg-panel/60 p-5">
        <div className="flex flex-wrap items-center gap-3">
          <div className="text-[11px] uppercase tracking-[0.28em] text-gold/75">Drift status</div>
          <span className={`border px-3 py-1 text-xs uppercase tracking-[0.2em] ${drift?.drifted ? "border-amber-400/30 text-amber-200" : "border-emerald-400/30 text-emerald-200"}`}>
            {drift?.available ? (drift.drifted ? "drift detected" : "baseline aligned") : "no baseline"}
          </span>
        </div>
        {!drift || !drift.available || !drift.snapshot ? (
          <div className="text-sm text-mist/55">Save a config snapshot first. Drift detection compares the current environment against a saved baseline.</div>
        ) : (
          <>
            <div className="text-sm text-mist/68">
              Comparing against <span className="text-white">{drift.snapshot.label}</span>, captured {formatTimestamp(drift.snapshot.capturedAt)}.
            </div>
            <div className="flex flex-wrap gap-3">
              <span className="border border-emerald-400/30 px-3 py-1 text-xs uppercase tracking-[0.2em] text-emerald-200">
                {drift.addedCount} added
              </span>
              <span className="border border-amber-400/30 px-3 py-1 text-xs uppercase tracking-[0.2em] text-amber-200">
                {drift.changedCount} changed
              </span>
              <span className="border border-rose-400/30 px-3 py-1 text-xs uppercase tracking-[0.2em] text-rose-200">
                {drift.removedCount} removed
              </span>
              <span className="border border-line px-3 py-1 text-xs uppercase tracking-[0.2em] text-mist/65">
                {drift.unchangedCount} unchanged
              </span>
            </div>
            {driftEntries.length === 0 ? (
              <div className="text-sm text-mist/55">No drift detected for the selected baseline.</div>
            ) : (
              <div className="space-y-3">
                {driftEntries.map((entry) => (
                  <ConfigDiffCard key={`drift-${entry.key}`} entry={entry} />
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {comparison ? (
        <div className="space-y-4 border border-line bg-panel/60 p-5">
          <div className="flex flex-wrap items-center gap-3">
            <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Diff summary</div>
            <span className="border border-emerald-400/30 px-3 py-1 text-xs uppercase tracking-[0.2em] text-emerald-200">
              {comparison.addedCount} added
            </span>
            <span className="border border-amber-400/30 px-3 py-1 text-xs uppercase tracking-[0.2em] text-amber-200">
              {comparison.changedCount} changed
            </span>
            <span className="border border-rose-400/30 px-3 py-1 text-xs uppercase tracking-[0.2em] text-rose-200">
              {comparison.removedCount} removed
            </span>
            <span className="border border-line px-3 py-1 text-xs uppercase tracking-[0.2em] text-mist/65">
              {comparison.unchangedCount} unchanged
            </span>
          </div>
          <div className="text-sm text-mist/68">
            Comparing current config against <span className="text-white">{comparison.snapshot.label}</span>.
          </div>
          {comparisonEntries.length === 0 ? (
            <div className="text-sm text-mist/55">No changed properties between the snapshot and current environment.</div>
          ) : (
            <div className="space-y-3">
              {comparisonEntries.map((entry) => (
                <ConfigDiffCard key={entry.key} entry={entry} />
              ))}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}

function ConfigDiffCard({ entry }: { entry: ConfigDiffEntryDescriptor }) {
  return (
    <div className="border border-line bg-shell/70 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="text-sm font-semibold text-white">{entry.key}</div>
        <div className="text-[11px] uppercase tracking-[0.24em] text-gold/75">{entry.status}</div>
      </div>
      <div className="mt-3 grid gap-4 md:grid-cols-2">
        <div>
          <div className="text-[11px] uppercase tracking-[0.22em] text-mist/45">Current</div>
          <div className="mt-2 text-sm text-white">{entry.currentValue || "Missing"}</div>
          <div className="mt-1 text-xs text-mist/55">{entry.currentPropertySource || "Not present"}</div>
        </div>
        <div>
          <div className="text-[11px] uppercase tracking-[0.22em] text-mist/45">Snapshot</div>
          <div className="mt-2 text-sm text-white">{entry.snapshotValue || "Missing"}</div>
          <div className="mt-1 text-xs text-mist/55">{entry.snapshotPropertySource || "Not present"}</div>
        </div>
      </div>
    </div>
  );
}

function FakeServicesPanel({
  service,
  pendingId,
  error,
  onToggle,
  selectedRouteId,
  onSelectedRouteIdChange,
  mockStatus,
  onMockStatusChange,
  mockContentType,
  onMockContentTypeChange,
  mockBody,
  onMockBodyChange,
  onSaveMock,
}: {
  service: FakeExternalServiceDescriptor | null;
  pendingId: string | null;
  error: string | null;
  onToggle: (serviceId: string, enabled: boolean) => void;
  selectedRouteId: string;
  onSelectedRouteIdChange: (value: string) => void;
  mockStatus: string;
  onMockStatusChange: (value: string) => void;
  mockContentType: string;
  onMockContentTypeChange: (value: string) => void;
  mockBody: string;
  onMockBodyChange: (value: string) => void;
  onSaveMock: (serviceId: string, routeId: string) => void;
}) {
  if (!service) {
    return (
      <div className="flex h-full min-h-[320px] items-center justify-center border border-dashed border-line text-sm text-mist/55">
        Select a fake external service to inspect details.
      </div>
    );
  }

  const isPending = pendingId === service.serviceId;
  const resolvedRouteId = selectedRouteId || service.mockResponses[0]?.routeId || "";
  const selectedMock = service.mockResponses.find((mock) => mock.routeId === resolvedRouteId) ?? service.mockResponses[0] ?? null;

  return (
    <div className="space-y-6">
      <div className="space-y-3 border-b border-line pb-5">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/80">Fake external service</div>
        <h2 className="text-2xl font-semibold tracking-[-0.04em] text-white sm:text-3xl">{service.displayName}</h2>
        <p className="max-w-2xl text-sm leading-6 text-mist/70">
          Enable a canned local HTTP surface under <code>{service.basePath}</code> so integrations can run against predictable external responses during local development.
        </p>
      </div>

      <DetailRow label="Service id" value={service.serviceId} />
      <DetailRow label="Base path" value={service.basePath} />
      <DetailRow label="Current state" value={service.enabled ? "Enabled" : "Disabled"} />
      <DetailRow label="Description" value={service.description} />

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => onToggle(service.serviceId, !service.enabled)}
          disabled={isPending}
          className="border border-accent px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isPending ? "Saving" : service.enabled ? "Disable stub" : "Enable stub"}
        </button>
        {error ? <div className="text-sm text-ember">{error}</div> : null}
      </div>

      <div className="space-y-4 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Mock response override</div>
        <p className="max-w-2xl text-sm leading-6 text-mist/70">
          Override an individual fake-service route with a custom status, content type, and raw body so staging and QA flows can exercise specific upstream responses without changing app code.
        </p>
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.22em] text-mist/45">Route</div>
          <select
            value={resolvedRouteId}
            onChange={(event) => onSelectedRouteIdChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          >
            {service.mockResponses.map((mock) => (
              <option key={mock.routeId} value={mock.routeId}>
                {mock.route}
              </option>
            ))}
          </select>
        </label>
        {selectedMock ? (
          <>
            <div className="grid gap-5 xl:grid-cols-2">
              <label className="block space-y-2">
                <div className="text-[11px] uppercase tracking-[0.22em] text-mist/45">Status</div>
                <input
                  aria-label="Mock status"
                  value={mockStatus}
                  onChange={(event) => onMockStatusChange(event.target.value)}
                  className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
                />
              </label>
              <label className="block space-y-2">
                <div className="text-[11px] uppercase tracking-[0.22em] text-mist/45">Content type</div>
                <input
                  aria-label="Mock content type"
                  value={mockContentType}
                  onChange={(event) => onMockContentTypeChange(event.target.value)}
                  className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
                />
              </label>
            </div>
            <label className="block space-y-2">
              <div className="text-[11px] uppercase tracking-[0.22em] text-mist/45">Response body</div>
              <textarea
                aria-label="Mock response body"
                value={mockBody}
                onChange={(event) => onMockBodyChange(event.target.value)}
                rows={8}
                className="w-full border border-line bg-black/20 p-4 text-sm leading-6 text-white outline-none"
              />
            </label>
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => onSaveMock(service.serviceId, resolvedRouteId)}
                disabled={isPending || !resolvedRouteId}
                className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
              >
                {isPending ? "Saving" : "Save mock response"}
              </button>
            </div>
            <CodeBlock value={JSON.stringify(selectedMock, null, 2)} label="Selected mock route" />
          </>
        ) : (
          <div className="text-sm text-mist/55">No route mocks available for this service.</div>
        )}
      </div>

      <CodeBlock value={JSON.stringify(service.routes, null, 2)} label="Routes" />
      <CodeBlock value={JSON.stringify(service, null, 2)} label="Resolved service" />
    </div>
  );
}

function TimeTravelPanel({
  state,
  pending,
  error,
  instant,
  zoneId,
  reason,
  durationMinutes,
  onInstantChange,
  onZoneChange,
  onReasonChange,
  onDurationMinutesChange,
  onApply,
  onReset,
}: {
  state: TimeTravelStateDescriptor | null;
  pending: boolean;
  error: string | null;
  instant: string;
  zoneId: string;
  reason: string;
  durationMinutes: string;
  onInstantChange: (value: string) => void;
  onZoneChange: (value: string) => void;
  onReasonChange: (value: string) => void;
  onDurationMinutesChange: (value: string) => void;
  onApply: () => void;
  onReset: () => void;
}) {
  if (!state) {
    return (
      <div className="flex h-full min-h-[320px] items-center justify-center border border-dashed border-line text-sm text-mist/55">
        Select the clock state to inspect time-travel controls.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="space-y-3 border-b border-line pb-5">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/80">Time travel clock</div>
        <h2 className="text-2xl font-semibold tracking-[-0.04em] text-white sm:text-3xl">{state.currentTime}</h2>
        <p className="max-w-2xl text-sm leading-6 text-mist/70">
          Override the auto-configured Spring <code>Clock</code> bean so local code paths that inject time can be pushed forward without changing system time.
        </p>
      </div>

      <DetailRow label="Current zone" value={state.zoneId} />
      <DetailRow label="Override status" value={state.overridden ? "Controlled override active" : "System clock active"} />
      {state.overrideReason ? <DetailRow label="Reason" value={state.overrideReason} /> : null}
      {state.overriddenBy ? <DetailRow label="Overridden by" value={state.overriddenBy} /> : null}
      {state.overriddenAt ? <DetailRow label="Overridden at" value={formatTimestamp(state.overriddenAt)} /> : null}
      {state.expiresAt ? <DetailRow label="Expires at" value={formatTimestamp(state.expiresAt)} /> : null}

      <div className="grid gap-5 xl:grid-cols-2">
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">ISO instant</div>
          <input
            value={instant}
            onChange={(event) => onInstantChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          />
        </label>
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Zone id</div>
          <input
            value={zoneId}
            onChange={(event) => onZoneChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          />
        </label>
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Reason</div>
          <input
            value={reason}
            onChange={(event) => onReasonChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          />
        </label>
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Expiry minutes</div>
          <input
            value={durationMinutes}
            onChange={(event) => onDurationMinutesChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          />
        </label>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={onApply}
          disabled={pending}
          className="border border-accent px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          {pending ? "Saving" : "Apply time override"}
        </button>
        <button
          type="button"
          onClick={onReset}
          disabled={pending || !state.overridden}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Reset clock
        </button>
        {error ? <div className="text-sm text-ember">{error}</div> : null}
      </div>

      <div className="text-sm text-mist/60">
        In staging mode, applying or resetting a time override requires the `admin` role header and the override can carry an expiry so it self-clears.
      </div>

      <CodeBlock value={JSON.stringify(state, null, 2)} label="Clock state" />
    </div>
  );
}

function SessionPanel({
  session,
  ownerName,
  allowGuests,
  shareRole,
  validateToken,
  shareResult,
  validationResult,
  pending,
  error,
  hostedSessionView,
  hostedSessionHistory,
  relayViewerSessions,
  relaySessionIdentity,
  ownerTransferTarget,
  hostedViewerMemberId,
  hostedViewerRole,
  hostedViewerSource,
  sessionArtifact,
  sessionNoteDraft,
  onOwnerNameChange,
  onAllowGuestsChange,
  onShareRoleChange,
  onValidateTokenChange,
  onHostedViewerMemberIdChange,
  onHostedViewerRoleChange,
  onHostedViewerSourceChange,
  onOwnerTransferTargetChange,
  onAttach,
  onOpenTunnel,
  onCloseTunnel,
  onHeartbeat,
  onSync,
  onStartRecording,
  onStopRecording,
  onShare,
  onValidate,
  onInspectRequestArtifact,
  onSessionNoteDraftChange,
  onAddDebugNote,
  onAddHostedViewer,
  onRemoveHostedViewer,
  onRevokeRelayViewerSession,
  onTransferOwner,
  onRotate,
  onRevoke,
}: {
  session: RemoteSessionDescriptor | null;
  ownerName: string;
  allowGuests: boolean;
  shareRole: string;
  validateToken: string;
  shareResult: SessionShareTokenDescriptor | null;
  validationResult: SessionAccessValidationDescriptor | null;
  pending: boolean;
  error: string | null;
  hostedSessionView: HostedSessionViewDescriptor | null;
  hostedSessionHistory: HostedSessionViewDescriptor[];
  relayViewerSessions: RelayViewerSessionDescriptor[];
  relaySessionIdentity: RelaySessionIdentityDescriptor | null;
  ownerTransferTarget: string;
  hostedViewerMemberId: string;
  hostedViewerRole: string;
  hostedViewerSource: string;
  sessionArtifact: CapturedRequest | null;
  sessionNoteDraft: string;
  onOwnerNameChange: (value: string) => void;
  onAllowGuestsChange: (value: boolean) => void;
  onShareRoleChange: (value: string) => void;
  onValidateTokenChange: (value: string) => void;
  onHostedViewerMemberIdChange: (value: string) => void;
  onHostedViewerRoleChange: (value: string) => void;
  onHostedViewerSourceChange: (value: string) => void;
  onOwnerTransferTargetChange: (value: string) => void;
  onAttach: () => void;
  onOpenTunnel: () => void;
  onCloseTunnel: () => void;
  onHeartbeat: () => void;
  onSync: () => void;
  onStartRecording: () => void;
  onStopRecording: () => void;
  onShare: () => void;
  onValidate: () => void;
  onInspectRequestArtifact: (requestId: string) => void;
  onSessionNoteDraftChange: (value: string) => void;
  onAddDebugNote: () => void;
  onAddHostedViewer: () => void;
  onRemoveHostedViewer: (memberId: string) => void;
  onRevokeRelayViewerSession: (viewerSessionId: string) => void;
  onTransferOwner: () => void;
  onRotate: () => void;
  onRevoke: () => void;
}) {
  if (!session) {
    return (
      <div className="flex h-full min-h-[320px] items-center justify-center border border-dashed border-line text-sm text-mist/55">
        Select the session state to inspect remote attach controls.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="space-y-3 border-b border-line pb-5">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/80">Remote attach foundation</div>
        <h2 className="text-2xl font-semibold tracking-[-0.04em] text-white sm:text-3xl">{session.ownerName}</h2>
        <p className="max-w-2xl text-sm leading-6 text-mist/70">
          Prepare a local agent session that can later attach to a hosted relay. This establishes the session id, role model, rotating owner token, and future share URL contract for team workflows.
        </p>
      </div>

      <DetailRow label="Agent status" value={session.agentStatus} />
      <DetailRow label="Relay status" value={session.relayStatus} />
      <DetailRow label="Relay mode" value={session.relayMode} />
      <DetailRow label="Tunnel status" value={session.tunnelStatus} />
      <DetailRow label="Session id" value={session.sessionId} />
      <DetailRow label="Relay lease" value={session.relayLeaseId ?? "n/a"} />
      <DetailRow label="Lease expires" value={formatTimestamp(session.relayLeaseExpiresAt)} />
      <DetailRow label="Hosted viewer" value={session.relayViewerUrl ?? "n/a"} />
      <DetailRow label="Tunnel id" value={session.relayTunnelId ?? "n/a"} />
      <DetailRow label="Tunnel opened" value={formatTimestamp(session.tunnelOpenedAt)} />
      <DetailRow label="Tunnel closed" value={formatTimestamp(session.tunnelClosedAt)} />
      <DetailRow label="Activity events" value={String(session.activity.length)} />
      <DetailRow label="Replay entries" value={String(session.replay.length)} />
      <DetailRow label="Access scope" value={session.accessScope} />
      <DetailRow label="Allowed roles" value={session.allowedRoles.join(", ")} />
      <DetailRow label="Owner seats" value={String(session.ownerCount)} />
      <DetailRow label="Viewer members" value={String(session.viewerMemberCount)} />
      <DetailRow label="Guest members" value={String(session.guestMemberCount)} />
      <DetailRow label="Viewer count" value={String(session.viewerCount)} />
      <DetailRow label="Active share tokens" value={String(session.activeShareTokens)} />
      <DetailRow label="Active members" value={String(session.activeMembers.length)} />
      <DetailRow label="Relay organization" value={session.relayOrganizationName ?? "n/a"} />
      <DetailRow label="Relay organization id" value={session.relayOrganizationId ?? "n/a"} />
      <DetailRow label="Relay owner account" value={session.relayOwnerAccountId ?? "n/a"} />
      <DetailRow label="Token preview" value={session.ownerTokenPreview} />
      <DetailRow label="Last share role" value={session.lastIssuedShareRole ?? "n/a"} />
      <DetailRow label="Last share token" value={session.lastIssuedShareTokenPreview} />
      <DetailRow label="Focused artifact" value={session.focusedArtifactId ? `${session.focusedArtifactType}:${session.focusedArtifactId}` : "n/a"} />
      <DetailRow label="Focused by" value={session.focusedBy ?? "n/a"} />
      <DetailRow label="Focused at" value={formatTimestamp(session.focusedAt)} />
      <DetailRow label="Sync status" value={session.syncStatus} />
      <DetailRow label="Last sync" value={formatTimestamp(session.lastSyncedAt)} />
      <DetailRow label="Last sync id" value={session.lastSyncId ?? "n/a"} />
      <DetailRow label="Session version" value={String(session.sessionVersion)} />
      <DetailRow label="Published version" value={session.publishedSessionVersion == null ? "n/a" : String(session.publishedSessionVersion)} />
      <DetailRow label="Hosted view status" value={session.hostedViewStatus} />
      <DetailRow label="Last published" value={formatTimestamp(session.lastPublishedAt)} />
      <DetailRow label="Activity retention" value={String(session.activityRetentionLimit)} />
      <DetailRow label="Replay retention" value={String(session.replayRetentionLimit)} />
      <DetailRow label="Recording retention" value={String(session.recordingRetentionLimit)} />
      <DetailRow label="Audit retention" value={String(session.auditRetentionLimit)} />
      <DetailRow label="Last heartbeat" value={formatTimestamp(session.lastHeartbeatAt)} />
      <DetailRow label="Next heartbeat" value={formatTimestamp(session.nextHeartbeatAt)} />
      <DetailRow label="Reconnect at" value={formatTimestamp(session.reconnectAt)} />
      <DetailRow label="Expires at" value={formatTimestamp(session.expiresAt)} />
      {session.lastError ? <DetailRow label="Last relay error" value={session.lastError} /> : null}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_auto]">
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Owner name</div>
          <input
            value={ownerName}
            onChange={(event) => onOwnerNameChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          />
        </label>
        <label className="flex items-end gap-3 text-sm text-mist/75">
          <input
            type="checkbox"
            checked={allowGuests}
            onChange={(event) => onAllowGuestsChange(event.target.checked)}
            className="h-4 w-4 border border-line bg-black/20"
          />
          Allow guest viewers
        </label>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Share role</div>
          <select
            value={shareRole}
            onChange={(event) => onShareRoleChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          >
            <option value="viewer">viewer</option>
            <option value="guest">guest</option>
          </select>
        </label>
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Validate token</div>
          <input
            value={validateToken}
            onChange={(event) => onValidateTokenChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
            placeholder="paste share token"
          />
        </label>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={onAttach}
          disabled={pending}
          className="border border-accent px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          {pending ? "Saving" : session.attached ? "Refresh attach session" : "Attach local session"}
        </button>
        <button
          type="button"
          onClick={onShare}
          disabled={pending || !session.attached}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Issue share token
        </button>
        <button
          type="button"
          onClick={onValidate}
          disabled={pending || !session.attached || !validateToken.trim()}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Validate token
        </button>
        <button
          type="button"
          onClick={onHeartbeat}
          disabled={pending || !session.attached}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Heartbeat relay
        </button>
        <button
          type="button"
          onClick={session.relayTunnelId ? onCloseTunnel : onOpenTunnel}
          disabled={pending || !session.attached}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          {session.relayTunnelId ? "Close tunnel" : "Open tunnel"}
        </button>
        <button
          type="button"
          onClick={onSync}
          disabled={pending || !session.attached}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Sync relay snapshot
        </button>
        <button
          type="button"
          onClick={session.recordingActive ? onStopRecording : onStartRecording}
          disabled={pending || !session.attached}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          {session.recordingActive ? "Stop recording" : "Start recording"}
        </button>
        <button
          type="button"
          onClick={onRotate}
          disabled={pending || !session.attached}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Rotate owner token
        </button>
        <button
          type="button"
          onClick={onRevoke}
          disabled={pending || !session.attached}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Revoke session
        </button>
        {error ? <div className="text-sm text-ember">{error}</div> : null}
      </div>

      {shareResult ? (
        <div className="space-y-2 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Issued share token</div>
          <DetailRow label="Role" value={shareResult.role} />
          <DetailRow label="Preview" value={shareResult.tokenPreview} />
          <DetailRow label="Share URL" value={shareResult.shareUrl} />
          <DetailRow label="Expires at" value={formatTimestamp(shareResult.expiresAt)} />
        </div>
      ) : null}

      {validationResult ? (
        <div className="space-y-2 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Validation result</div>
          <DetailRow label="Allowed" value={String(validationResult.allowed)} />
          <DetailRow label="Role" value={validationResult.role} />
          <DetailRow label="Reason" value={validationResult.reason} />
          <DetailRow label="Viewer count" value={String(validationResult.viewerCount)} />
        </div>
      ) : null}

      <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Team session viewer</div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <TeamStatCard label="Owner seats" value={String(session.ownerCount)} />
          <TeamStatCard label="Viewer members" value={String(session.viewerMemberCount)} />
          <TeamStatCard label="Guest members" value={String(session.guestMemberCount)} />
          <TeamStatCard label="Recent actors" value={String(session.recentActors.length)} />
        </div>
        <DetailRow
          label="Shared focus"
          value={session.focusedArtifactId ? `${session.focusedArtifactType}:${session.focusedArtifactId}` : "No shared artifact focus"}
        />
        <CodeBlock
          value={session.recentActors.length > 0 ? JSON.stringify(session.recentActors, null, 2) : "[]"}
          label="Recent actors"
        />
      </div>

      <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Multi developer view</div>
        {session.workspaceMembers.length > 0 ? (
          <div className="space-y-3">
            {session.workspaceMembers.map((member) => (
              <div key={member.memberId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
                <DetailRow label="Member" value={member.memberId} />
                <DetailRow label="Role" value={member.role} />
                <DetailRow label="Source" value={member.source} />
                <DetailRow label="Joined" value={formatTimestamp(member.joinedAt)} />
                <DetailRow label="Last seen" value={formatTimestamp(member.lastSeenAt)} />
                <DetailRow label="Last action" value={member.lastAction} />
                <DetailRow
                  label="Focused artifact"
                  value={member.focusedArtifactId ? `${member.focusedArtifactType}:${member.focusedArtifactId}` : "none"}
                />
              </div>
            ))}
          </div>
        ) : (
          <div className="text-sm text-mist/55">No multi-developer workspace state yet.</div>
        )}
      </div>

      <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Collaborative debugging</div>
        <DetailRow
          label="Note target"
          value={session.focusedArtifactId ? `${session.focusedArtifactType}:${session.focusedArtifactId}` : "Session-wide note"}
        />
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Debug note</div>
          <textarea
            value={sessionNoteDraft}
            onChange={(event) => onSessionNoteDraftChange(event.target.value)}
            rows={4}
            className="w-full border border-line bg-black/20 p-4 text-sm leading-6 text-white outline-none"
            placeholder="Add a debugging observation, hypothesis, or next step"
          />
        </label>
        <button
          type="button"
          onClick={onAddDebugNote}
          disabled={pending || !session.attached || !sessionNoteDraft.trim()}
          className="border border-line px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Add debug note
        </button>
        {session.debugNotes.length > 0 ? (
          <div className="space-y-3">
            {session.debugNotes.map((note) => (
              <div key={note.noteId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
                <DetailRow label="Author" value={note.author} />
                <DetailRow label="At" value={formatTimestamp(note.createdAt)} />
                <DetailRow label="Target" value={note.artifactId ? `${note.artifactType}:${note.artifactId}` : "session"} />
                <DetailRow label="Message" value={note.message} />
              </div>
            ))}
          </div>
        ) : (
          <div className="text-sm text-mist/55">No collaborative debug notes yet.</div>
        )}
      </div>

      <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Dev session recording</div>
        <DetailRow label="Status" value={session.recordingActive ? "Recording" : "Idle"} />
        <DetailRow label="Recording id" value={session.currentRecordingId ?? "Not recording"} />
        <DetailRow label="Started at" value={formatTimestamp(session.recordingStartedAt)} />
        <DetailRow label="Stopped at" value={formatTimestamp(session.recordingStoppedAt)} />
        {session.recordings.length > 0 ? (
          <div className="space-y-3">
            {session.recordings.map((recording) => (
              <div key={recording.recordingId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
                <DetailRow label="Recording" value={recording.recordingId} />
                <DetailRow label="Active" value={String(recording.active)} />
                <DetailRow label="Started by" value={recording.startedBy} />
                <DetailRow label="Started at" value={formatTimestamp(recording.startedAt)} />
                <DetailRow label="Stopped at" value={formatTimestamp(recording.stoppedAt)} />
                <DetailRow label="Activity count" value={String(recording.activityCount)} />
                <DetailRow label="Replay count" value={String(recording.replayCount)} />
                <DetailRow label="Debug note count" value={String(recording.debugNoteCount)} />
                <DetailRow label="Active member count" value={String(recording.activeMemberCount)} />
                <DetailRow
                  label="Focus"
                  value={recording.focusedArtifactId ? `${recording.focusedArtifactType}:${recording.focusedArtifactId}` : "none"}
                />
                <div className="sm:col-span-2">
                  <CodeBlock value={JSON.stringify(recording.highlights, null, 2)} label="Highlights" />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-sm text-mist/55">No session recordings captured yet.</div>
        )}
      </div>

      {session.activeMembers.length > 0 ? (
        <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Active members</div>
          {session.activeMembers.map((member) => (
            <div key={member.memberId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
              <DetailRow label="Member" value={member.memberId} />
              <DetailRow label="Role" value={member.role} />
              <DetailRow label="Source" value={member.source} />
              <DetailRow label="Joined" value={formatTimestamp(member.joinedAt)} />
              <DetailRow label="Last seen" value={formatTimestamp(member.lastSeenAt)} />
            </div>
          ))}
        </div>
      ) : null}

      {session.activity.length > 0 ? (
        <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Session activity</div>
          {session.activity.map((event, index) => (
            <div key={`${event.eventType}-${event.timestamp}-${index}`} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
              <DetailRow label="Event" value={event.eventType} />
              <DetailRow label="Actor" value={event.actor} />
              <DetailRow label="Detail" value={event.detail} />
              <DetailRow label="At" value={formatTimestamp(event.timestamp)} />
            </div>
          ))}
        </div>
      ) : null}

      {session.audit.length > 0 ? (
        <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Audit policy feed</div>
          {session.audit.map((event) => (
            <div key={event.auditId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
              <DetailRow label="Event" value={event.eventType} />
              <DetailRow label="Actor" value={event.actor} />
              <DetailRow label="Detail" value={event.detail} />
              <DetailRow label="Recorded" value={formatTimestamp(event.recordedAt)} />
            </div>
          ))}
        </div>
      ) : null}

      <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Hosted viewer snapshot</div>
        {hostedSessionView ? (
          <>
            <DetailRow label="View id" value={hostedSessionView.viewId} />
            <DetailRow label="Sync id" value={hostedSessionView.syncId ?? "n/a"} />
            <DetailRow label="Durable state" value={hostedSessionView.durableState} />
            <DetailRow label="Published version" value={hostedSessionView.publishedVersion == null ? "n/a" : String(hostedSessionView.publishedVersion)} />
            <DetailRow label="Replay count" value={String(hostedSessionView.replayCount)} />
            <DetailRow label="Debug notes" value={String(hostedSessionView.debugNoteCount)} />
            <DetailRow label="Recordings" value={String(hostedSessionView.recordingCount)} />
            <DetailRow label="Recent replay titles" value={hostedSessionView.recentReplayTitles.length > 0 ? hostedSessionView.recentReplayTitles.join(", ") : "n/a"} />
            <div className="space-y-3 border-t border-line/50 pt-3">
              <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Hosted viewer presence</div>
              <div className="grid gap-3 sm:grid-cols-3">
                <label className="space-y-2 text-[11px] uppercase tracking-[0.22em] text-mist/50">
                  <span>Member id</span>
                  <input
                    value={hostedViewerMemberId}
                    onChange={(event) => onHostedViewerMemberIdChange(event.target.value)}
                    className="w-full border border-line bg-black/20 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-accent"
                  />
                </label>
                <label className="space-y-2 text-[11px] uppercase tracking-[0.22em] text-mist/50">
                  <span>Role</span>
                  <select
                    value={hostedViewerRole}
                    onChange={(event) => onHostedViewerRoleChange(event.target.value)}
                    className="w-full border border-line bg-black/20 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-accent"
                  >
                    <option value="viewer">viewer</option>
                    <option value="guest">guest</option>
                  </select>
                </label>
                <label className="space-y-2 text-[11px] uppercase tracking-[0.22em] text-mist/50">
                  <span>Source</span>
                  <input
                    value={hostedViewerSource}
                    onChange={(event) => onHostedViewerSourceChange(event.target.value)}
                    className="w-full border border-line bg-black/20 px-3 py-2 text-sm normal-case tracking-normal text-white outline-none focus:border-accent"
                  />
                </label>
              </div>
              <button
                type="button"
                onClick={onAddHostedViewer}
                disabled={pending || hostedViewerMemberId.trim().length === 0}
                className="border border-line px-4 py-2 text-xs uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
              >
                Add hosted viewer
              </button>
            </div>
            {hostedSessionView.members.length > 0 ? (
              <div className="space-y-3 border-t border-line/50 pt-3">
                <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Hosted viewer members</div>
                {hostedSessionView.members.map((member) => (
                  <div key={member.memberId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
                    <DetailRow label="Member" value={member.memberId} />
                    <DetailRow label="Role" value={member.role} />
                    <DetailRow label="Source" value={member.source} />
                    <DetailRow label="Joined" value={formatTimestamp(member.joinedAt)} />
                    <DetailRow label="Published" value={formatTimestamp(member.publishedAt)} />
                    <DetailRow label="Last action" value={member.lastAction} />
                    <DetailRow
                      label="Focus"
                      value={member.focusedArtifactId ? `${member.focusedArtifactType}:${member.focusedArtifactId}` : "none"}
                    />
                    {member.memberId !== "owner" ? (
                      <div className="sm:col-span-2">
                        <button
                          type="button"
                          onClick={() => onRemoveHostedViewer(member.memberId)}
                          disabled={pending}
                          className="border border-line px-4 py-2 text-xs uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
                        >
                          Remove hosted viewer
                        </button>
                      </div>
                    ) : null}
                  </div>
                ))}
              </div>
            ) : null}
          </>
        ) : (
          <div className="text-sm text-mist/55">Hosted view projection unavailable.</div>
        )}
      </div>

      {hostedSessionHistory.length > 0 ? (
        <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Hosted publication history</div>
          {hostedSessionHistory.map((view) => (
            <div key={`${view.viewId}-${view.publishedVersion ?? "draft"}`} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
              <DetailRow label="View id" value={view.viewId} />
              <DetailRow label="Sync id" value={view.syncId ?? "n/a"} />
              <DetailRow label="Version" value={view.publishedVersion == null ? "n/a" : String(view.publishedVersion)} />
              <DetailRow label="Published" value={formatTimestamp(view.lastPublishedAt)} />
              <DetailRow label="Members" value={String(view.members.length)} />
              <DetailRow label="Replay count" value={String(view.replayCount)} />
            </div>
          ))}
        </div>
      ) : null}

      <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Relay identity and ownership</div>
        {relaySessionIdentity ? (
          <>
            <DetailRow label="Organization" value={relaySessionIdentity.organization?.organizationName ?? "n/a"} />
            <DetailRow label="Organization id" value={relaySessionIdentity.organization?.organizationId ?? "n/a"} />
            <DetailRow label="Relay owner" value={relaySessionIdentity.owner?.displayName ?? "n/a"} />
            <DetailRow label="Owner account id" value={relaySessionIdentity.owner?.accountId ?? "n/a"} />
            <DetailRow label="Collaborators" value={String(relaySessionIdentity.collaborators.length)} />
            {relaySessionIdentity.collaborators.length > 0 ? (
              <div className="space-y-3 border-t border-line/50 pt-3">
                {relaySessionIdentity.collaborators.map((collaborator) => (
                  <div key={collaborator.accountId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
                    <DetailRow label="Collaborator" value={collaborator.displayName} />
                    <DetailRow label="Account id" value={collaborator.accountId} />
                    <DetailRow label="Role" value={collaborator.role} />
                    <DetailRow label="Org id" value={collaborator.organizationId} />
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-sm text-mist/55">No relay-owned collaborators yet.</div>
            )}
          </>
        ) : (
          <div className="text-sm text-mist/55">Relay identity unavailable.</div>
        )}
      </div>

      <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Relay-owned viewer sessions</div>
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.22em] text-mist/45">Transfer relay ownership</div>
          <select
            value={ownerTransferTarget}
            onChange={(event) => onOwnerTransferTargetChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          >
            <option value="">select viewer session</option>
            {relayViewerSessions.map((viewerSession) => (
              <option key={viewerSession.viewerSessionId} value={viewerSession.viewerSessionId}>
                {viewerSession.viewerName} ({viewerSession.role})
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          onClick={onTransferOwner}
          disabled={pending || !ownerTransferTarget}
          className="border border-line px-4 py-2 text-xs uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          Transfer relay owner
        </button>
        {relayViewerSessions.length > 0 ? (
          relayViewerSessions.map((viewerSession) => (
            <div key={viewerSession.viewerSessionId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
              <DetailRow label="Viewer session" value={viewerSession.viewerSessionId} />
              <DetailRow label="Viewer name" value={viewerSession.viewerName} />
              <DetailRow label="Role" value={viewerSession.role} />
              <DetailRow label="Created" value={formatTimestamp(viewerSession.createdAt)} />
              <DetailRow label="Expires" value={formatTimestamp(viewerSession.expiresAt)} />
              <div className="sm:col-span-2">
                <button
                  type="button"
                  onClick={() => onRevokeRelayViewerSession(viewerSession.viewerSessionId)}
                  disabled={pending}
                  className="border border-line px-4 py-2 text-xs uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Revoke viewer session
                </button>
              </div>
            </div>
          ))
        ) : (
          <div className="text-sm text-mist/55">No active relay-owned viewer sessions.</div>
        )}
      </div>

      {session.replay.length > 0 ? (
        <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Session replay</div>
          {session.replay.map((entry) => (
            <div key={entry.replayId} className="grid gap-2 border-t border-line/50 pt-3 first:border-t-0 first:pt-0 sm:grid-cols-2">
              <DetailRow label="Category" value={entry.category} />
              <DetailRow label="Title" value={entry.title} />
              <DetailRow label="Preview" value={entry.payloadPreview} />
              <DetailRow label="Artifact type" value={entry.artifactType ?? "n/a"} />
              <DetailRow label="Artifact id" value={entry.artifactId ?? "n/a"} />
              <DetailRow label="Occurred" value={formatTimestamp(entry.occurredAt)} />
              {entry.artifactType === "request" && entry.artifactId ? (
                <div className="sm:col-span-2">
                  <button
                    type="button"
                    onClick={() => onInspectRequestArtifact(entry.artifactId!)}
                    disabled={pending}
                    className="border border-line px-4 py-2 text-xs uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    Inspect request artifact
                  </button>
                </div>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}

      {sessionArtifact ? (
        <div className="space-y-3 border border-line/60 bg-black/15 p-4 text-sm text-mist/75">
          <div className="text-[11px] uppercase tracking-[0.28em] text-accent/75">Focused request artifact</div>
          <DetailRow label="Request id" value={sessionArtifact.requestId} />
          <DetailRow label="Path" value={sessionArtifact.path} />
          <DetailRow label="Status" value={String(sessionArtifact.responseStatus)} />
          <DetailRow label="Captured at" value={formatTimestamp(sessionArtifact.timestamp)} />
          <CodeBlock value={JSON.stringify(sessionArtifact, null, 2)} label="Request artifact" />
        </div>
      ) : null}

      <CodeBlock value={JSON.stringify(session, null, 2)} label="Session contract" />
    </div>
  );
}

function WebhookSimulatorPanel({
  targets,
  method,
  onMethodChange,
  selectedPath,
  onSelectedPathChange,
  body,
  onBodyChange,
  headers,
  onHeadersChange,
  sending,
  error,
  result,
  onSend,
}: {
  targets: WebhookTargetDescriptor[];
  method: string;
  onMethodChange: (value: string) => void;
  selectedPath: string;
  onSelectedPathChange: (value: string) => void;
  body: string;
  onBodyChange: (value: string) => void;
  headers: string;
  onHeadersChange: (value: string) => void;
  sending: boolean;
  error: string | null;
  result: ApiTestResult | null;
  onSend: () => void;
}) {
  return (
    <div className="space-y-6">
      <div className="space-y-3 border-b border-line pb-5">
        <div className="text-[11px] uppercase tracking-[0.28em] text-accent/80">API testing</div>
        <h2 className="text-2xl font-semibold tracking-[-0.04em] text-white sm:text-3xl">Send a request through the local app.</h2>
        <p className="max-w-2xl text-sm leading-6 text-mist/70">
          Use discovered local endpoints as suggestions, send an ad hoc request through the running Spring app, and inspect the real response without leaving `/_dev`.
        </p>
      </div>

      <div className="grid gap-5 xl:grid-cols-[180px_minmax(0,1fr)]">
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Method</div>
          <select
            value={method}
            onChange={(event) => onMethodChange(event.target.value)}
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          >
            {["GET", "POST", "PUT", "PATCH", "DELETE"].map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>

        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Target path</div>
          <input
            value={selectedPath}
            onChange={(event) => onSelectedPathChange(event.target.value)}
            list="webhook-target-paths"
            className="w-full border border-line bg-black/20 px-4 py-3 text-sm text-white outline-none"
          />
          <datalist id="webhook-target-paths">
            {targets.map((target) => (
              <option key={webhookKey(target)} value={target.path}>
                {target.method} {target.controller}
              </option>
            ))}
          </datalist>
        </label>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Headers JSON</div>
          <textarea
            value={headers}
            onChange={(event) => onHeadersChange(event.target.value)}
            rows={8}
            className="w-full border border-line bg-black/20 p-4 text-sm leading-6 text-white outline-none"
          />
        </label>
        <label className="block space-y-2">
          <div className="text-[11px] uppercase tracking-[0.28em] text-mist/45">Body JSON</div>
          <textarea
            value={body}
            onChange={(event) => onBodyChange(event.target.value)}
            rows={8}
            className="w-full border border-line bg-black/20 p-4 text-sm leading-6 text-white outline-none"
          />
        </label>
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onSend}
          disabled={sending || !selectedPath}
          className="border border-accent px-5 py-3 text-sm uppercase tracking-[0.22em] text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          {sending ? "Sending" : "Send request"}
        </button>
        {error ? <div className="text-sm text-ember">{error}</div> : null}
      </div>

      {result ? (
        <div className="space-y-4">
          <DetailRow label="Response status" value={`${result.method} ${result.path} -> ${result.status}`} />
          <CodeBlock value={result.responseBody || "Empty response body"} label="Response body" />
        </div>
      ) : null}
    </div>
  );
}

function rowClass(isActive: boolean): string {
  return [
    "block w-full border-b border-line px-5 py-4 text-left transition",
    isActive ? "bg-white/5" : "hover:bg-white/[0.03]",
  ].join(" ");
}

function formatTimestamp(value: string | null): string {
  return value ? new Date(value).toLocaleString() : "n/a";
}

function labelForCount(tab: TabId): string {
  switch (tab) {
    case "endpoints":
      return "matching handler entries";
    case "requests":
      return "matching request snapshots";
    case "config":
      return "matching config values";
    case "featureFlags":
      return "matching feature flags";
    case "dependencies":
      return "matching bean relationships";
    case "time":
      return "clock state";
    case "session":
      return "remote session contract";
    case "logs":
      return "matching log events";
    case "jobs":
      return "matching scheduled jobs";
    case "dbQueries":
      return "matching database queries";
    case "webhooks":
      return "matching API test targets";
    case "fakeServices":
      return "matching fake external services";
    case "auditLogs":
      return "matching audit events";
  }
}

function tabTitle(tab: TabId): string {
  return TABS.find((item) => item.id === tab)?.label ?? "Data";
}

function statusBadgeClass(status: "loading" | "ready" | "error"): string {
  if (status === "ready") {
    return "text-accent";
  }
  if (status === "error") {
    return "text-ember";
  }
  return "text-gold";
}

function levelClass(level: string): string {
  if (level === "ERROR") {
    return "text-xs uppercase tracking-[0.24em] text-ember";
  }
  if (level === "WARN") {
    return "text-xs uppercase tracking-[0.24em] text-gold";
  }
  return "text-xs uppercase tracking-[0.24em] text-accent";
}
