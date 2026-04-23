package com.devtools.examples.actuator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ActuatorDemoApplication {

    private static final Logger log = LoggerFactory.getLogger(ActuatorDemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ActuatorDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner appStartedRunner() {
        return args -> log.info("Actuator demo started. Open http://localhost:8081/_dev");
    }
}
