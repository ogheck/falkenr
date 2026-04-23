package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DevToolsUiAccessDisabledIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.devtools.ui.access.mode=staging",
                "spring.devtools.ui.access.enabled=false",
                "spring.devtools.ui.access.auth-token=staging-secret",
                "spring.devtools.ui.access.allowed-roles[0]=viewer"
        }
)
@AutoConfigureMockMvc
class DevToolsUiAccessDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksDashboardWhenAccessIsGloballyDisabled() throws Exception {
        mockMvc.perform(get("/_dev")
                        .header("X-DevTools-Auth", "staging-secret")
                        .header("X-DevTools-Role", "viewer")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.20");
                            return request;
                        }))
                .andExpect(status().isServiceUnavailable());
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
