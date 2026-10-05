package com.shop.auth.config;

import com.shop.common.security.AccessTokenService;
import com.shop.common.security.InternalSecurity;
import com.shop.common.security.InternalTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, InternalTokenService internalTokens) throws Exception {
        InternalSecurity.baseline(http, internalTokens);
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**").permitAll()
                // "public" = no user login needed; the gateway-signed internal token is still mandatory
                .requestMatchers(HttpMethod.POST,
                        "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AccessTokenService accessTokenService(@Value("${app.security.jwt-secret}") String secret,
                                          @Value("${app.security.access-token-ttl-seconds}") long ttl) {
        return new AccessTokenService(secret, ttl);
    }
}
