package com.devtools.ui.relay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.devtools.ui.relay-server.persistence-file=${java.io.tmpdir}/spring-devtools-ui-relay-server-notfound-state-${random.uuid}.json",
        "spring.devtools.ui.relay-server.auth-secret=test-relay-secret",
        "spring.devtools.ui.relay-server.auth-issuer=test-relay-issuer"
})
@AutoConfigureMockMvc
class RelayNotFoundJsonTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingRouteReturnsJsonNotWhitelabel() throws Exception {
        mockMvc.perform(get("/definitely-not-a-route"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("404"))
                .andExpect(jsonPath("$.path").value("/definitely-not-a-route"));
    }
}

