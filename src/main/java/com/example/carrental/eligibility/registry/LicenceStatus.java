package com.example.carrental.eligibility.registry;

/**
 * All the rental company needs to know: may this person drive or not. Why a licence is not valid
 * (expired, suspended, unknown) is none of our business, so a real registry adapter maps every such
 * answer to {@link #INVALID}. That also keeps personal data we receive to the minimum.
 */
public enum LicenceStatus {
    VALID,
    INVALID
}
