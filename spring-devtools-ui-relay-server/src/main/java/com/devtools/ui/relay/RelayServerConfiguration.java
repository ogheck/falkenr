package com.devtools.ui.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RelayServerConfiguration {

    @Bean
    InMemoryRelaySessionStore relaySessionStore(RelayServerProperties properties, ObjectMapper objectMapper) {
        return new InMemoryRelaySessionStore(properties, objectMapper);
    }
}
