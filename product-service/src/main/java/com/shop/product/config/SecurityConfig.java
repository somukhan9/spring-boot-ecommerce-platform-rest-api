package com.shop.product.config;

import com.shop.common.security.InternalSecurity;
import com.shop.common.security.InternalTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, InternalTokenService tokens) throws Exception {
        InternalSecurity.baseline(http, tokens);
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*").permitAll()   // public catalog
                .anyRequest().authenticated());   // writes + /internal/** need a real user context; roles via @PreAuthorize
        return http.build();
    }
}
