package com.chaostensor.whisperwrapper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Test security config to disable security for tests.
 */
@Configuration
@Profile("test")
public class TestSecurityConfig {

    @Bean
    public SecurityWebFilterChain testFilterChain(ServerHttpSecurity http) throws Exception {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().permitAll());
        return http.build();
    }
}