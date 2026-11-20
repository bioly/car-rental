package com.example.carrental.identity;

import java.time.LocalDate;

/** Registration input. toString deliberately omits every field that is secret or PII. */
public record RegisterCommand(String email, String password, String fullName,
                              LocalDate dateOfBirth, String licenceNumber) {

    @Override
    public String toString() {
        return "RegisterCommand[redacted]";
    }
}
