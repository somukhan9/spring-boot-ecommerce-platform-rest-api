package com.shop.common.config;

import com.shop.common.security.InternalTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalTokenConfig {

    @Bean
    public InternalTokenService internalTokenService(
            @Value("${app.security.internal-secret}") String secret,
            @Value("${app.security.internal-token-ttl-seconds:60}") long ttl) {
        return new InternalTokenService(secret, ttl);
    }
}
