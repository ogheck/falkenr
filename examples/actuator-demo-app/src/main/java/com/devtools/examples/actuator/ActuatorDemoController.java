package com.devtools.examples.actuator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class ActuatorDemoController {

    private static final Logger log = LoggerFactory.getLogger(ActuatorDemoController.class);

    @GetMapping("/health-check")
    public Map<String, Object> healthCheck() {
        log.info("Actuator demo health check requested");
        return Map.of(
                "status", "ok",
                "time", Instant.now().toString()
        );
    }

    @Scheduled(fixedDelay = 30000L)
    void emitHeartbeat() {
        log.info("Actuator demo heartbeat");
    }
}
