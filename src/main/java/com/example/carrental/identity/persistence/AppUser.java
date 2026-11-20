package com.example.carrental.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_app_user_email", columnNames = "email"))
/**
 * A user account: customer or administrator. Holds personal data (PII): the licence number is only ever displayed
 * masked. Created only through the factory methods.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** PII: masked everywhere it is displayed. Stored as plain text here; see README, "Left out on purpose". */
    @Column(name = "licence_number", length = 32)
    private String licenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    private AppUser(String email, String passwordHash, String fullName, LocalDate dateOfBirth,
                    String licenceNumber, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.licenceNumber = licenceNumber;
        this.role = role;
    }

    /** A normal customer account. The password must already be hashed and the licence number is the raw value. */
    public static AppUser customer(String email, String passwordHash, String fullName,
                            LocalDate dateOfBirth, String licenceNumber) {
        return new AppUser(email, passwordHash, fullName, dateOfBirth, licenceNumber, Role.USER);
    }

    /** An administrator has no date of birth or licence number. */
    public static AppUser admin(String email, String passwordHash) {
        return new AppUser(email, passwordHash, "Administrator", null, null, Role.ADMIN);
    }
}
