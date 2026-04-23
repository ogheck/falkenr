package com.devtools.ui.relay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Cloudflare (and most proxies) terminate TLS upstream and forward the original scheme/host via
 * X-Forwarded-* headers. Spring Security's OAuth redirect-uri building relies on the request URL,
 * so we must apply forwarded headers early.
 */
@Configuration
class RelayForwardedHeadersConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}

