package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevToolsUiFeatureControlsIntegrationTest {

    @SpringBootTest(
            classes = DevToolsUiIntegrationTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "features.checkout=false",
                    "spring.devtools.ui.features.requests=false",
                    "spring.devtools.ui.features.logs=false",
                    "spring.devtools.ui.features.db-queries=false",
                    "spring.devtools.ui.features.feature-flags=false",
                    "spring.devtools.ui.features.fake-services=false",
                    "spring.devtools.ui.features.webhooks=false",
                    "spring.devtools.ui.features.time=false",
                    "spring.datasource.url=jdbc:h2:mem:devtoolsui-feature-toggles;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            }
    )
    @AutoConfigureMockMvc
    static class FeatureTogglesDisabled {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void disabledFeaturesReturnEmptyDataAndRejectMutations() throws Exception {
            mockMvc.perform(get("/_dev/api/requests").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/logs").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/db-queries").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/feature-flags").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/fake-services").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/webhooks/targets").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/time").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].overridden").value(false));

            mockMvc.perform(post("/_dev/api/feature-flags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "key": "features.checkout",
                                      "enabled": true
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isConflict());

            mockMvc.perform(post("/_dev/api/fake-services")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "serviceId": "github",
                                      "enabled": true
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isConflict());

            mockMvc.perform(post("/_dev/api/webhooks/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "path": "/webhooks/github",
                                      "body": "{}"
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isConflict());

            mockMvc.perform(post("/_dev/api/time")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "instant": "2026-04-08T16:30:00Z",
                                      "zoneId": "UTC"
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isConflict());

            mockMvc.perform(get("/_dev/fake/github/status").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isNotFound());
        }
    }

    @SpringBootTest(
            classes = DevToolsUiIntegrationTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "spring.devtools.ui.request-limit=0",
                    "spring.devtools.ui.log-limit=0",
                    "spring.devtools.ui.db-query-limit=0",
                    "spring.datasource.url=jdbc:h2:mem:devtoolsui-budgets;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            }
    )
    @AutoConfigureMockMvc
    static class ZeroMemoryBudgets {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void zeroBudgetsPreventStatefulCapture() throws Exception {
            mockMvc.perform(post("/echo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"zero-budget\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/hello"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/db/users"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/_dev/api/requests").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/logs").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mockMvc.perform(get("/_dev/api/db-queries").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
        }
    }

    @SpringBootTest(
            classes = DevToolsUiIntegrationTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "spring.devtools.ui.remote.session-activity-limit=2",
                    "spring.devtools.ui.remote.session-replay-limit=2",
                    "spring.devtools.ui.remote.session-recording-limit=1",
                    "spring.devtools.ui.remote.session-audit-limit=2",
                    "spring.datasource.url=jdbc:h2:mem:devtoolsui-session-policy;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            }
    )
    @AutoConfigureMockMvc
    static class SessionPolicyBudgets {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void sessionRetentionBudgetsAndAuditFeedAreExposed() throws Exception {
            mockMvc.perform(post("/_dev/api/session/attach")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "ownerName": "policy-owner",
                                      "allowGuests": true
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isOk());

            String shareResponse = mockMvc.perform(post("/_dev/api/session/share")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "role": "guest"
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String shareUrl = DevToolsUiIntegrationTest.JsonTestFieldExtractor.extract(shareResponse, "shareUrl");
            String issuedToken = shareUrl.substring(shareUrl.indexOf("?token=") + 7);

            mockMvc.perform(post("/_dev/api/session/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "token": "%s"
                                    }
                                    """.formatted(issuedToken))
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/_dev/api/session/recording/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "actor": "policy-owner"
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/_dev/api/session/recording/stop")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "actor": "policy-owner"
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/_dev/api/session/recording/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "actor": "policy-owner"
                                    }
                                    """)
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                return request;
                            }))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/_dev/api/session").with(request -> {
                        request.setRemoteAddr("127.0.0.1");
                        return request;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].activityRetentionLimit").value(2))
                    .andExpect(jsonPath("$.items[0].replayRetentionLimit").value(2))
                    .andExpect(jsonPath("$.items[0].recordingRetentionLimit").value(1))
                    .andExpect(jsonPath("$.items[0].auditRetentionLimit").value(2))
                    .andExpect(jsonPath("$.items[0].activity.length()").value(2))
                    .andExpect(jsonPath("$.items[0].replay.length()").value(2))
                    .andExpect(jsonPath("$.items[0].recordings.length()").value(1))
                    .andExpect(jsonPath("$.items[0].audit.length()").value(2))
                    .andExpect(jsonPath("$.items[0].audit[0].eventType").exists());
        }
    }

    @SpringBootTest(
            classes = DevToolsUiIntegrationTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "features.checkout=false",
                    "spring.devtools.ui.access.mode=staging",
                    "spring.devtools.ui.access.auth-token=test-token",
                    "spring.devtools.ui.access.allowed-roles[0]=viewer",
                    "spring.devtools.ui.access.allowed-roles[1]=admin",
                    "spring.devtools.ui.feature-flags.require-admin-role-for-mutations=true",
                    "spring.devtools.ui.feature-flags.definitions-file=${java.io.tmpdir}/spring-devtools-ui-feature-flag-definitions-staging-test.json",
                    "spring.datasource.url=jdbc:h2:mem:devtoolsui-feature-flags-staging;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            }
    )
    @AutoConfigureMockMvc
    static class StagingFeatureFlagMutationControls {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void featureFlagMutationsRequireAdminRoleInStaging() throws Exception {
            mockMvc.perform(post("/_dev/api/feature-flags/definitions")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "key": "features.checkout",
                                      "displayName": "Checkout rollout",
                                      "owner": "platform-team"
                                    }
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/feature-flags/definitions")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "key": "features.checkout",
                                      "displayName": "Checkout rollout",
                                      "owner": "platform-team"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.key").value("features.checkout"));
        }
    }

    @SpringBootTest(
            classes = DevToolsUiIntegrationTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "spring.devtools.ui.access.mode=staging",
                    "spring.devtools.ui.access.auth-token=test-token",
                    "spring.devtools.ui.access.allowed-roles[0]=viewer",
                    "spring.devtools.ui.access.allowed-roles[1]=admin",
                    "spring.datasource.url=jdbc:h2:mem:devtoolsui-time-staging;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            }
    )
    @AutoConfigureMockMvc
    static class StagingTimeTravelMutationControls {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void timeTravelMutationsRequireAdminRoleInStaging() throws Exception {
            mockMvc.perform(post("/_dev/api/time")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "instant": "2026-04-08T16:30:00Z",
                                      "zoneId": "UTC",
                                      "reason": "viewer test",
                                      "durationMinutes": 15
                                    }
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/time")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "instant": "2026-04-08T16:30:00Z",
                                      "zoneId": "UTC",
                                      "reason": "admin test",
                                      "durationMinutes": 15
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overridden").value(true))
                    .andExpect(jsonPath("$.overriddenBy").value("admin"))
                    .andExpect(jsonPath("$.overrideReason").value("admin test"));
        }
    }

    @SpringBootTest(
            classes = DevToolsUiIntegrationTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "spring.devtools.ui.access.mode=staging",
                    "spring.devtools.ui.access.auth-token=test-token",
                    "spring.devtools.ui.access.allowed-roles[0]=viewer",
                    "spring.devtools.ui.access.allowed-roles[1]=admin",
                    "spring.datasource.url=jdbc:h2:mem:devtoolsui-rbac;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            }
    )
    @AutoConfigureMockMvc
    static class StagingRbacMutationControls {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void accessIdentityExposesPermissionsByRole() throws Exception {
            mockMvc.perform(get("/_dev/api/access/identity")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("viewer"))
                    .andExpect(jsonPath("$.permissions.length()").value(0));

            mockMvc.perform(get("/_dev/api/access/identity")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("admin"))
                    .andExpect(jsonPath("$.permissions.length()").value(8))
                    .andExpect(jsonPath("$.permissions[0]").value("config.write"));
        }

        @Test
        void viewerCannotMutateProtectedFeaturesWhileAdminCan() throws Exception {
            mockMvc.perform(post("/_dev/api/config/snapshots")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "viewer baseline"
                                    }
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/config/snapshots")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "admin baseline"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.label").value("admin baseline"));

            mockMvc.perform(post("/_dev/api/fake-services")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "serviceId": "github",
                                      "enabled": true
                                    }
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/fake-services")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "serviceId": "github",
                                      "enabled": true
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serviceId").value("github"));

            mockMvc.perform(post("/_dev/api/session/attach")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "ownerName": "viewer-owner",
                                      "allowGuests": true
                                    }
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/session/attach")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "ownerName": "admin-owner",
                                      "allowGuests": true
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownerName").value("admin-owner"));

            mockMvc.perform(post("/_dev/api/webhooks/send")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "path": "/webhooks/github",
                                      "body": "{\\\"ok\\\":true}"
                                    }
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/webhooks/send")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "path": "/webhooks/github",
                                      "body": "{\\\"ok\\\":true}"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value("/webhooks/github"));

            mockMvc.perform(post("/_dev/api/api-testing/send")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "method": "POST",
                                      "path": "/echo",
                                      "body": "{\\\"name\\\":\\\"viewer\\\"}"
                                    }
                                    """))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/api-testing/send")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "method": "POST",
                                      "path": "/echo",
                                      "body": "{\\\"name\\\":\\\"admin\\\"}"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }
    }

    @SpringBootTest(
            classes = DevToolsUiIntegrationTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "spring.devtools.ui.access.mode=staging",
                    "spring.devtools.ui.access.auth-token=test-token",
                    "spring.devtools.ui.access.allowed-roles[0]=viewer",
                    "spring.devtools.ui.access.allowed-roles[1]=admin",
                    "spring.devtools.ui.access.approval.enabled=true",
                    "spring.devtools.ui.access.approval.ttl-minutes=30",
                    "spring.datasource.url=jdbc:h2:mem:devtoolsui-approval;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver"
            }
    )
    @AutoConfigureMockMvc
    static class StagingApprovalControls {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void highRiskMutationsRequireApprovedRequest() throws Exception {
            mockMvc.perform(post("/_dev/api/time")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "instant": "2026-04-08T16:30:00Z",
                                      "zoneId": "UTC",
                                      "reason": "without approval"
                                    }
                                    """))
                    .andExpect(status().isPreconditionRequired());

            String approvalResponse = mockMvc.perform(post("/_dev/api/approvals")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "permission": "time.write",
                                      "target": "/_dev/api/time",
                                      "reason": "Need to validate scheduled rollover"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("pending"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String approvalId = DevToolsUiIntegrationTest.JsonTestFieldExtractor.extract(approvalResponse, "approvalId");

            mockMvc.perform(post("/_dev/api/approvals/{approvalId}/approve", approvalId)
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "viewer"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/_dev/api/approvals/{approvalId}/approve", approvalId)
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("approved"))
                    .andExpect(jsonPath("$.approvedBy").value("admin"));

            mockMvc.perform(post("/_dev/api/time")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin")
                            .header("X-DevTools-Approval", approvalId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "instant": "2026-04-08T16:30:00Z",
                                      "zoneId": "UTC",
                                      "reason": "with approval"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overridden").value(true));

            mockMvc.perform(get("/_dev/api/approvals")
                            .header("X-DevTools-Auth", "test-token")
                            .header("X-DevTools-Role", "admin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].approvalId").value(approvalId))
                    .andExpect(jsonPath("$.items[0].status").value("consumed"));
        }
    }
}
