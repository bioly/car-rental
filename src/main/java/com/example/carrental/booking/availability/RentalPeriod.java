package com.example.carrental.booking.availability;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Half-open time interval {@code [start, end)} during which a car is out on rental.
 * Half-open means a return at 10:00 and a pick-up at 10:00 do not conflict.
 */
public record RentalPeriod(Instant start, Instant end) {

    public RentalPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    /** A rental of {@code days} calendar days in the zone of {@code start} (DST-safe: same local time of day). */
    public static RentalPeriod of(ZonedDateTime start, int days) {
        return new RentalPeriod(start.toInstant(), start.plusDays(days).toInstant());
    }

    /** Half-open intervals: a rental ending at 10:00 does not overlap one starting at 10:00. */
    boolean overlaps(RentalPeriod other) {
        // return start.isBefore(other.end) && other.start.isBefore(end);
        return !start.isAfter(other.end) && !other.start.isAfter(end);
    }
}
