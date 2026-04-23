package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DevToolsUiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.devtools.ui.remote.simulate-heartbeat-failure=true",
                "spring.datasource.url=jdbc:h2:mem:relayheartbeat;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
        }
)
@AutoConfigureMockMvc
class RemoteSessionRelayHeartbeatFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void failedHeartbeatMovesSessionIntoReconnectState() throws Exception {
        mockMvc.perform(post("/_dev/api/session/attach")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "pair-debugger",
                                  "allowGuests": false
                                }
                                """)
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relayStatus").value("connected"))
                .andExpect(jsonPath("$.relayMode").value("local-stub"))
                .andExpect(jsonPath("$.relayLeaseId").isNotEmpty())
                .andExpect(jsonPath("$.relayLeaseExpiresAt").isNotEmpty());

        mockMvc.perform(post("/_dev/api/session/heartbeat")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relayStatus").value("reconnecting"))
                .andExpect(jsonPath("$.tunnelStatus").value("stale"))
                .andExpect(jsonPath("$.relayLeaseExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.reconnectAt").isNotEmpty())
                .andExpect(jsonPath("$.lastError").value("Relay heartbeat failed"));
    }
}
