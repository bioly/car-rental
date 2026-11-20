package com.example.carrental;

import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.RegisterCommand;
import com.example.carrental.identity.RegistrationService;
import com.example.carrental.identity.UserView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Helpers shared by the integration tests. */
public final class TestSupport {

    public static final String PASSWORD = "Password123!";
    public static final String VALID_LICENCE = "DL12345678";

    private TestSupport() {
    }

    /** 10:00 UTC, one year from now plus {@code dayOffset} days. Each test uses its own offset to avoid overlaps. */
    public static LocalDateTime pickup(int dayOffset) {
        return LocalDateTime.now(ZoneOffset.UTC).plusYears(1).plusDays(dayOffset)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    public static LocalDate dobForAge(int years) {
        return LocalDate.now(ZoneOffset.UTC).minusYears(years).minusDays(30);
    }

    public static UserView register(RegistrationService registration, String licence, LocalDate dob) {
        String email = "user-" + UUID.randomUUID() + "@test.example";
        return registration.register(new RegisterCommand(email, PASSWORD, "Test Driver", dob, licence));
    }

    public static UserView registerAdult(RegistrationService registration) {
        return register(registration, VALID_LICENCE, dobForAge(40));
    }

    /** Makes sure at least {@code minimum} active cars of the type exist. */
    public static void ensureCars(FleetService fleet, CarType type, int minimum) {
        while (fleet.capacityOf(type) < minimum) {
            fleet.addCar("T-" + UUID.randomUUID().toString().substring(0, 8), type);
        }
    }
}
