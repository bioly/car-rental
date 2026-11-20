package com.example.carrental.eligibility.registry;

import java.time.LocalDate;

/** What the government registry says about a licence. {@code expiryDate} is the last day the licence is valid; it is null when the licence is INVALID. */
public record LicenceRecord(LicenceStatus status, LocalDate expiryDate) {
}
