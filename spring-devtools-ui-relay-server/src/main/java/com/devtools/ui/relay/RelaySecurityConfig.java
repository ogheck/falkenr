package com.devtools.ui.relay;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
class RelaySecurityConfig {

    @Bean
    SecurityFilterChain relaySecurityFilterChain(HttpSecurity http,
                                                 RelayWebLoginSuccessHandler successHandler,
                                                 ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        // The relay is an API-first service. We rely on:
        // - OAuth2 login for hosted dashboard access
        // - scoped bearer tokens (accountSession/viewerSession) for session access
        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(auth -> auth
                // Public viewer surfaces.
                .requestMatchers("/s/**", "/view/**").permitAll()
                .requestMatchers("/sessions/viewer/**").permitAll()
                .requestMatchers("/sessions/status").permitAll()
                .requestMatchers("/app").permitAll()

                // Local agents attach/sync without browser auth.
                .requestMatchers(
                        "/sessions/attach",
                        "/sessions/heartbeat",
                        "/sessions/sync",
                        "/sessions/tunnel/**",
                        "/sessions/access-tokens/register",
                        "/sessions/artifacts/**",
                        "/sessions/collaboration",
                        "/sessions/*/identity",
                        "/sessions/*/viewer-sessions/**",
                        "/sessions/*/hosted-view/**",
                        "/sessions/*/hosted-history",
                        "/sessions/*/hosted-view",
                        "/sessions/*/audit",
                        "/sessions/*/owner/transfer",
                        "/sessions/*/invitations",
                        "/sessions/invitations/accept"
                ).permitAll()

                // Hosted dashboard APIs are protected by scoped accountSession tokens (and optionally OAuth for minting them).
                .requestMatchers("/sessions/dashboard/**", "/sessions/directory/**").permitAll()

                // Admin endpoints: keep protected via login for now.
                .requestMatchers("/sessions/admin/**").authenticated()

                .anyRequest().permitAll()
        );

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(successHandler));
        }

        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/app")
        );

        http.httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
