package com.shop.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Baseline Spring Security setup shared by all services. Each service adds its own URL rules. */
public final class InternalSecurity {
    private InternalSecurity() {}

    public static HttpSecurity baseline(HttpSecurity http, InternalTokenService tokens) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)            // stateless, header-token API
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalAuthFilter(tokens), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> JsonErrors.write(res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> JsonErrors.write(res, 403, "Access denied")));
    }
}
