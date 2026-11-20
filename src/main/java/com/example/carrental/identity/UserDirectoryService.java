package com.example.carrental.identity;

import com.example.carrental.identity.persistence.AppUser;
import com.example.carrental.identity.persistence.AppUserRepository;
import com.example.carrental.identity.persistence.DriverProfile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access for other packages: what the booking flow needs to know about a driver, and nothing more.
 */
@Service
public class UserDirectoryService {

    private final AppUserRepository users;

    UserDirectoryService(AppUserRepository users) {
        this.users = users;
    }

    /** @throws IdentityException.UserNotFound if the user does not exist */
    @Transactional(readOnly = true)
    public DriverProfile profileFor(long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IdentityException.UserNotFound(userId));
        return new DriverProfile(user.getId(), user.getFullName(), user.getDateOfBirth(), user.getLicenceNumber());
    }
}
