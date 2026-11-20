package com.example.carrental.identity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence for users. Other features use the identity services, not this repository.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Emails are stored lower-case, so lookups are case-insensitive. */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
