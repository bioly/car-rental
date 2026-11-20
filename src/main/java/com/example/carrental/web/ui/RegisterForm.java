package com.example.carrental.web.ui;

import com.example.carrental.identity.RegisterCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Mutable form-backing bean (Thymeleaf binds field by field). Deliberately no toString: it holds a password. */
@Getter
@Setter
public class RegisterForm {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72, message = "must be 8 to 72 characters")
    private String password;

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @NotNull
    @Past
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{8,12}$", message = "must be 8 to 12 letters or digits")
    private String licenceNumber;

    RegisterCommand toCommand() {
        return new RegisterCommand(email, password, fullName, dateOfBirth, licenceNumber);
    }
}
