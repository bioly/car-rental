package com.example.carrental.identity;

import com.example.carrental.identity.persistence.AppUser;
import com.example.carrental.identity.persistence.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Demo customers, only when {@code app.demo-data} is true (the default, see {@code application.yml}; tests switch it off).
 * Their licence numbers trigger the different behaviours of the fake government service (see README).
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@Order(4)
@Slf4j
class DemoUsersLoader implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "Password123!";
    private static final String FIRST_DEMO_EMAIL = "alice@example.com";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    DemoUsersLoader(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(FIRST_DEMO_EMAIL)) {
            return;   // already loaded (e.g. a persistent database); never duplicate
        }
        demo(FIRST_DEMO_EMAIL, "Alice Driver", LocalDate.of(1988, 5, 14), "DL12345678");
        demo("young@example.com", "Yara Young", LocalDate.of(2004, 3, 2), "DL22222222");
        demo("invalid@example.com", "Ian Invalid", LocalDate.of(1985, 1, 1), "INV00000001");
        demo("shortlicence@example.com", "Sally Short", LocalDate.of(1990, 9, 9), "SHORT000001");
        demo("flaky@example.com", "Finn Flaky", LocalDate.of(1992, 2, 2), "FAIL00000001");
        demo("slow@example.com", "Sloane Slow", LocalDate.of(1991, 11, 11), "SLOW00000001");
        log.info("Demo users loaded");
    }

    private void demo(String email, String name, LocalDate dob, String licence) {
        users.save(AppUser.customer(email, passwordEncoder.encode(DEMO_PASSWORD), name, dob, licence));
    }
}
