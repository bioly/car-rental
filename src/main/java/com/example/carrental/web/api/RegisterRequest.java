package com.example.carrental.web.api;

import com.example.carrental.identity.RegisterCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body of {@code POST /api/v1/users}. Bean Validation rejects bad input with a 400 before the service
 * is called; {@code toString} is redacted so it can never leak into logs.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{8,12}$", message = "must be 8 to 12 letters or digits") String licenceNumber) {

    RegisterCommand toCommand() {
        return new RegisterCommand(email, password, fullName, dateOfBirth, licenceNumber);
    }

    @Override
    public String toString() {
        return "RegisterRequest[redacted]";   // secrets and PII never reach logs
    }
}
