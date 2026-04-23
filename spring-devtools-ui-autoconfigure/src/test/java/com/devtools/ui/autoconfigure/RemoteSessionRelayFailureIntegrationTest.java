package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DevToolsUiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.devtools.ui.remote.simulate-attach-failure=true",
                "spring.datasource.url=jdbc:h2:mem:relayattach;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
        }
)
@AutoConfigureMockMvc
class RemoteSessionRelayFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void failedAttachLeavesSessionInUnavailableReconnectState() throws Exception {
        mockMvc.perform(post("/_dev/api/session/attach")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "pair-debugger",
                                  "allowGuests": true
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attached").value(true))
                .andExpect(jsonPath("$.relayMode").value("local-stub"))
                .andExpect(jsonPath("$.allowedRoles", hasItems("owner", "viewer", "guest")))
                .andExpect(jsonPath("$.relayStatus").value("unavailable"))
                .andExpect(jsonPath("$.tunnelStatus").value("offline"))
                .andExpect(jsonPath("$.relayConnectionId").isEmpty())
                .andExpect(jsonPath("$.relayLeaseId").isEmpty())
                .andExpect(jsonPath("$.reconnectAt").isNotEmpty())
                .andExpect(jsonPath("$.lastError", containsString("Managed relay handshake failed")));

        mockMvc.perform(get("/_dev/api/session").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].relayStatus").value("unavailable"))
                .andExpect(jsonPath("$.items[0].relayMode").value("local-stub"));
    }
}
