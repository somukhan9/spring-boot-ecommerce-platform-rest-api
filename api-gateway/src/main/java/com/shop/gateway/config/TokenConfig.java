package com.shop.gateway.config;

import com.shop.common.security.AccessTokenService;
import com.shop.common.security.InternalTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenConfig {

    @Bean
    AccessTokenService accessTokenService(@Value("${app.security.jwt-secret}") String secret) {
        return new AccessTokenService(secret, 0);   // gateway only verifies, never issues
    }

    @Bean
    InternalTokenService internalTokenService(@Value("${app.security.internal-secret}") String secret,
                                              @Value("${app.security.internal-token-ttl-seconds}") long ttl) {
        return new InternalTokenService(secret, ttl);
    }
}
