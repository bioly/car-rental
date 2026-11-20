package com.example.carrental.identity;

/**
 * Business failures of user management; mapped to HTTP statuses in the web layer.
 */
public class IdentityException extends RuntimeException {

    protected IdentityException(String message) {
        super(message);
    }

    public static class EmailAlreadyRegistered extends IdentityException {
        public EmailAlreadyRegistered() {
            super("This email address is already registered");
        }
    }

    public static class UserNotFound extends IdentityException {
        public UserNotFound(long id) {
            super("User " + id + " not found");
        }
    }
}
