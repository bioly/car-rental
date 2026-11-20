package com.example.carrental.identity.security;

import com.example.carrental.identity.persistence.Role;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/** The authenticated user as seen by controllers: carries the user id so nothing needs to re-query by email. */
public class AppPrincipal extends User {

    private final long id;

    public AppPrincipal(long id, String email, String passwordHash, boolean enabled, Role role) {
        super(email, passwordHash, enabled, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
