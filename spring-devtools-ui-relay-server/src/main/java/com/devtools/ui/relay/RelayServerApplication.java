package com.devtools.ui.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RelayServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RelayServerApplication.class, args);
    }
}
