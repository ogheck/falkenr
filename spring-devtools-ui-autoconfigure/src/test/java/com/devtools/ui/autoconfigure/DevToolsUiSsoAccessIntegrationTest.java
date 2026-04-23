package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DevToolsUiSsoAccessIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.devtools.ui.access.mode=sso",
                "spring.devtools.ui.access.allowed-roles[0]=viewer",
                "spring.devtools.ui.access.allowed-roles[1]=admin",
                "spring.devtools.ui.access.sso.allowed-domains[0]=example.com",
                "spring.devtools.ui.access.sso.admin-groups[0]=devtools-admin",
                "spring.devtools.ui.access.sso.viewer-groups[0]=devtools-viewer"
        }
)
@AutoConfigureMockMvc
class DevToolsUiSsoAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksSsoDashboardWithoutTrustedIdentityHeaders() throws Exception {
        mockMvc.perform(get("/_dev").with(request -> {
                    request.setRemoteAddr("203.0.113.20");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsSsoDashboardForTrustedViewerIdentity() throws Exception {
        mockMvc.perform(get("/_dev")
                        .header("X-Forwarded-User", "alice")
                        .header("X-Forwarded-Email", "alice@example.com")
                        .header("X-Forwarded-Groups", "devtools-viewer")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.20");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/_dev/index.html"));
    }

    @Test
    void rejectsSsoIdentityFromWrongDomain() throws Exception {
        mockMvc.perform(get("/_dev")
                        .header("X-Forwarded-User", "mallory")
                        .header("X-Forwarded-Email", "mallory@other.com")
                        .header("X-Forwarded-Groups", "devtools-viewer")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.20");
                            return request;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesDerivedSsoIdentityAndUsesItForAuditActors() throws Exception {
        mockMvc.perform(get("/_dev/api/access/identity")
                        .header("X-Forwarded-User", "alice")
                        .header("X-Forwarded-Email", "alice@example.com")
                        .header("X-Forwarded-Groups", "devtools-admin, engineering")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.20");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("sso"))
                .andExpect(jsonPath("$.actor").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.groups[0]").value("devtools-admin"))
                .andExpect(jsonPath("$.permissions[0]").value("config.write"))
                .andExpect(jsonPath("$.permissions[7]").value("approvals.review"));

        mockMvc.perform(post("/_dev/api/config/snapshots")
                        .header("X-Forwarded-User", "alice")
                        .header("X-Forwarded-Email", "alice@example.com")
                        .header("X-Forwarded-Groups", "devtools-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "sso baseline"
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.20");
                            return request;
                        }))
                .andExpect(status().isOk());

        mockMvc.perform(get("/_dev/api/audit-logs")
                        .header("X-Forwarded-User", "alice")
                        .header("X-Forwarded-Email", "alice@example.com")
                        .header("X-Forwarded-Groups", "devtools-admin")
                        .param("q", "sso baseline")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.20");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actor").value("alice@example.com"))
                .andExpect(jsonPath("$.items[0].detail", containsString("sso baseline")));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @RestController
        static class SampleController {

            @GetMapping("/hello")
            String hello() {
                return "hello";
            }
        }
    }
}
