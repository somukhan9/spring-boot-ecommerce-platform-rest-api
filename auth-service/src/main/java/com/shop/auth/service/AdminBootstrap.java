package com.shop.auth.service;

import com.shop.auth.domain.User;
import com.shop.auth.repository.UserRepository;
import com.shop.common.security.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Creates the first ADMIN from ADMIN_EMAIL / ADMIN_PASSWORD env vars if no such user exists. */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.bootstrap-admin.email:}")
    private String email;
    @Value("${app.bootstrap-admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) return;
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(e)) return;
        User admin = new User();
        admin.setEmail(e);
        admin.setFullName("Platform Admin");
        admin.setPasswordHash(encoder.encode(password));
        admin.setRoles(Set.of(Role.ADMIN));
        users.save(admin);
        log.info("Bootstrap admin created: {}", e);
    }
}
