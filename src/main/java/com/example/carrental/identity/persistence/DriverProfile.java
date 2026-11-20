package com.example.carrental.identity.persistence;

import com.example.carrental.privacy.Masking;

import java.time.LocalDate;

/** What the booking flow needs to know about a driver. PII: never print the raw licence number. */
public record DriverProfile(long userId, String fullName, LocalDate dateOfBirth, String licenceNumber) {

    @Override
    public String toString() {
        return "DriverProfile[userId=" + userId + ", licence=" + Masking.licence(licenceNumber) + "]";
    }
}
