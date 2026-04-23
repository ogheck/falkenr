package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.CapturedRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DevToolsUiRequestPersistenceIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.devtools.ui.history.persist-requests=true",
                "spring.devtools.ui.history.requests-persistence-file=${java.io.tmpdir}/spring-devtools-ui-request-history-${random.uuid}.json"
        }
)
@AutoConfigureMockMvc
class DevToolsUiRequestPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevToolsUiProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsCapturedRequestsToConfiguredJsonFile() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"persist-me\"}"))
                .andExpect(status().isOk());

        Path persistenceFile = Path.of(properties.getHistory().getRequestsPersistenceFile());
        assertThat(Files.exists(persistenceFile)).isTrue();

        List<CapturedRequest> requests = objectMapper.readValue(Files.readAllBytes(persistenceFile), new TypeReference<>() {
        });
        assertThat(requests).isNotEmpty();
        assertThat(requests.get(requests.size() - 1).path()).isEqualTo("/echo");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        EchoController echoController() {
            return new EchoController();
        }
    }

    @RestController
    static class EchoController {

        @PostMapping("/echo")
        String echo(@RequestBody String body) {
            return body;
        }
    }
}
