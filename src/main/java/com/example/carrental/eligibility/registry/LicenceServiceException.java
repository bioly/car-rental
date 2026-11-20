package com.example.carrental.eligibility.registry;

/** Technical (transient) failure talking to the registry. Adapters throw this; the resilience layer handles it. */
public class LicenceServiceException extends RuntimeException {

    public LicenceServiceException(String message) {
        super(message);
    }

    public LicenceServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
