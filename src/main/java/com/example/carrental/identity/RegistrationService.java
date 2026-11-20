package com.example.carrental.identity;

import com.example.carrental.identity.persistence.AppUser;
import com.example.carrental.identity.persistence.AppUserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Self-registration of customers. Normalises the email, hashes the password and stores the licence number;
 * the returned view only ever contains a masked licence number.
 */
@Service
@Slf4j
public class RegistrationService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    RegistrationService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /** @throws IdentityException.EmailAlreadyRegistered if the email is taken */
    @Transactional
    public UserView register(RegisterCommand command) {
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new IdentityException.EmailAlreadyRegistered();
        }
        AppUser user = AppUser.customer(
                email,
                passwordEncoder.encode(command.password()),
                command.fullName().trim(),
                command.dateOfBirth(),
                command.licenceNumber().trim().toUpperCase(Locale.ROOT));
        try {
            user = users.saveAndFlush(user);   // flush now so a concurrent duplicate surfaces here
        } catch (DataIntegrityViolationException e) {
            throw new IdentityException.EmailAlreadyRegistered();
        }
        log.info("User registered id={}", user.getId());   // ids only: no email, name or licence in logs
        return UserView.from(user);
    }
}
