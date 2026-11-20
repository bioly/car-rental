package com.example.carrental.identity;

import com.example.carrental.identity.persistence.AppUser;
import com.example.carrental.identity.persistence.Role;
import com.example.carrental.privacy.Masking;

/** Safe projection of a user: the licence number is always masked. */
public record UserView(long id, String email, String fullName, Role role,
                       boolean enabled, String maskedLicence) {

    /** Safe projection for display: the licence number is masked. */
    public static UserView from(AppUser u) {
        return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isEnabled(),
                Masking.licence(u.getLicenceNumber()));
    }
}
