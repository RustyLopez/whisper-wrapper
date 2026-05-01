package com.chaostensor.whisperwrapper.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * NOTE: Security: Caution: For now hosting only as an internal docker service used by a separate app in the same docker env.
 * The app currently exposes the transcribe operation, though that does let you specify a known path.
 * so in theory there is some risk of extracting transcripts from videos on disk if this were opened up.
 * not an issue for now but we'll probably want to add some server to server auth solution when we push this into eks.
 */
@Configuration
@Profile("internal-only")
@Slf4j
public class DevOrInternOnlySecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.warn("This configuration is only intended for dev or localhost envs. Please setup auth if using in production.");
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}