package com.devtools.examples.maven;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
public class MavenConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MavenConsumerApplication.class, args);
    }

    @RestController
    static class MavenConsumerController {
        @GetMapping("/hello")
        Map<String, Object> hello() {
            return Map.of("message", "hello from maven");
        }
    }
}
