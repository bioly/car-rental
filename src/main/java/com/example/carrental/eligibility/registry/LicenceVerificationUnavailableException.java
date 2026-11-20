package com.example.carrental.eligibility.registry;

/**
 * The registry could not be consulted (timeout, retries exhausted, circuit open, bulkhead full).
 * Callers fail closed: no reservation without a verified licence.
 */
public class LicenceVerificationUnavailableException extends RuntimeException {

    public LicenceVerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
