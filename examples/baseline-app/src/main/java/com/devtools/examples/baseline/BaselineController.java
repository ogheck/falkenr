package com.devtools.examples.baseline;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class BaselineController {

    @GetMapping("/hello")
    Map<String, Object> hello() {
        return Map.of("message", "hello from baseline");
    }
}
