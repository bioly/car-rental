package com.example.carrental.privacy;

/** PII masking helpers: anything shown in a UI, API response or log goes through here. */
public final class Masking {

    private Masking() {
    }

    /** {@code DL12345678} becomes {@code ****5678}; short or missing values become {@code ****}. */
    public static String licence(String licenceNumber) {
        if (licenceNumber == null || licenceNumber.length() <= 4) {
            return "****";
        }
        return "****" + licenceNumber.substring(licenceNumber.length() - 4);
    }
}
