package com.devtools.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    @Value("${demo.banner:Falkenr Demo}")
    private String banner;

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        log.info("Listing demo users");
        return List.of(
                Map.of("id", 1, "name", "Ada Lovelace"),
                Map.of("id", 2, "name", "Grace Hopper"),
                Map.of("id", 3, "name", "Margaret Hamilton")
        );
    }

    @GetMapping("/users/{id}")
    public Map<String, Object> user(@PathVariable int id) {
        log.info("Looking up user {}", id);
        return Map.of("id", id, "name", "User " + id);
    }

    @GetMapping("/config-check")
    public Map<String, Object> configCheck() {
        log.info("Reading config banner");
        return Map.of("banner", banner, "serverPort", "8080");
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> payload) {
        log.info("Echo payload keys: {}", payload.keySet());
        return Map.of("received", payload, "size", payload.size());
    }
}
