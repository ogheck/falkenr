package com.devtools.ui.core.endpoint;

import com.devtools.ui.core.model.EndpointDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(EndpointCollectorTest.TestConfig.class)
@WebAppConfiguration
class EndpointCollectorTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void collectExpandsPathsAndMethodsAndSortsResults() {
        EndpointCollector collector = new EndpointCollector(List.<RequestMappingInfoHandlerMapping>of(handlerMapping));

        List<EndpointDescriptor> endpoints = collector.collect();

        assertThat(endpoints)
                .contains(new EndpointDescriptor("GET", "/alpha", "TestController", "alpha"))
                .contains(new EndpointDescriptor("GET", "/beta-one", "TestController", "beta"))
                .contains(new EndpointDescriptor("POST", "/beta-one", "TestController", "beta"))
                .contains(new EndpointDescriptor("GET", "/beta-two", "TestController", "beta"))
                .contains(new EndpointDescriptor("POST", "/beta-two", "TestController", "beta"))
                .contains(new EndpointDescriptor("ALL", "/open", "TestController", "open"));

        assertThat(endpoints)
                .filteredOn(endpoint -> endpoint.path().startsWith("/beta"))
                .extracting(EndpointDescriptor::method)
                .containsExactly("GET", "POST", "GET", "POST");
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/alpha")
        String alpha() {
            return "alpha";
        }

        @RequestMapping(path = {"/beta-one", "/beta-two"}, method = {RequestMethod.GET, RequestMethod.POST})
        String beta() {
            return "beta";
        }

        @RequestMapping("/open")
        String open() {
            return "open";
        }
    }
}
