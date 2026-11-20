package com.example.carrental.identity;

import com.example.carrental.identity.persistence.AppUser;
import com.example.carrental.identity.persistence.AppUserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the administrator account on startup if it does not exist yet.
 *
 * <p>Required (without it nobody could use the admin dashboard). It is idempotent: restarting never creates a second admin or resets the password.
 * The initial password comes from {@code APP_ADMIN_PASSWORD}; the default in {@code application.yml} is for
 * development only.
 */
@Component
@Order(2)
@Slf4j
class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
                   @Value("${app.admin.email}") String adminEmail,
                   @Value("${app.admin.password}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmail(adminEmail)) {
            users.save(AppUser.admin(adminEmail, passwordEncoder.encode(adminPassword)));
            log.info("Administrator account created");
        }
    }
}
