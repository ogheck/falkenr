package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DevToolsUiProdProfileDisabledTest.ProdProfileApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class DevToolsUiProdProfileDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void devtoolsUiIsDisabledWhenProdProfileIsActive() throws Exception {
        assertThat(applicationContext.containsBean("devToolsApiController")).isFalse();
        assertThat(applicationContext.containsBean("devToolsUiController")).isFalse();

        mockMvc.perform(get("/_dev").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isNotFound());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ProdProfileApplication {
    }
}
