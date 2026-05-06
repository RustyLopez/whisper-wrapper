package com.chaostensor.whisperwrapper.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * NOTE: Security: Caution: For now hosting only as an internal docker service used by a separate app in the same docker env.
 * The app currently exposes the transcribe operation, though that does let you specify a known path.
 * so in theory there is some risk of extracting transcripts from videos on disk if this were opened up.
 * not an issue for now but we'll probably want to add some server to server auth solution when we push this into eks.
 */
@Configuration
@Profile({"internal-only", "test"})
@Slf4j
public class DevOrInternOnlySecurityConfig {


    /**
     * NOTe this ist he web flux way...
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) throws Exception {
        log.warn("This configuration is only intended for dev or localhost envs. Please setup auth if using in production.");
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().permitAll());
        return http.build();
    }
}