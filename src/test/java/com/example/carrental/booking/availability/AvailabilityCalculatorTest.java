package com.example.carrental.booking.availability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityCalculatorTest {

    private static final Instant DAY = Instant.parse("2030-06-10T00:00:00Z");

    /** Period from hour {@code from} (inclusive) to hour {@code to} (exclusive) of the test day. */
    private static RentalPeriod hours(int from, int to) {
        return new RentalPeriod(DAY.plus(Duration.ofHours(from)), DAY.plus(Duration.ofHours(to)));
    }

    private final AvailabilityCalculator calculator = new AvailabilityCalculator();

    @Test
    @DisplayName("no existing reservations: any capacity >= 1 is enough")
    void emptyFleetUsage() {
        assertThat(calculator.peakUsage(hours(10, 12), List.of())).isZero();
        assertThat(calculator.canAccommodate(1, hours(10, 12), List.of())).isTrue();
    }

    @Test
    @DisplayName("capacity 0 never accommodates")
    void zeroCapacity() {
        assertThat(calculator.canAccommodate(0, hours(10, 12), List.of())).isFalse();
        assertThat(calculator.remaining(0, hours(10, 12), List.of())).isZero();
    }

    @Test
    @DisplayName("a partially overlapping reservation uses the car")
    void partialOverlapBlocksSingleCar() {
        List<RentalPeriod> existing = List.of(hours(8, 11));
        assertThat(calculator.canAccommodate(1, hours(10, 14), existing)).isFalse();
        assertThat(calculator.canAccommodate(2, hours(10, 14), existing)).isTrue();
    }

    @Test
    @DisplayName("back-to-back rentals do not conflict (half-open intervals)")
    void backToBackIsAllowed() {
        assertThat(calculator.canAccommodate(1, hours(12, 14), List.of(hours(10, 12)))).isTrue();
        assertThat(calculator.canAccommodate(1, hours(8, 10), List.of(hours(10, 12)))).isTrue();
    }

    @Test
    @DisplayName("peak concurrency matters, not the number of overlapping reservations")
    void sequentialReservationsNeverUseMoreThanOneCar() {
        // Both overlap the request, but never each other, so only one car is in use at any instant.
        List<RentalPeriod> existing = List.of(hours(8, 12), hours(12, 16));
        assertThat(calculator.peakUsage(hours(10, 14), existing)).isEqualTo(1);
        assertThat(calculator.canAccommodate(2, hours(10, 14), existing)).isTrue();
        assertThat(calculator.canAccommodate(1, hours(10, 14), existing)).isFalse();
    }

    @Test
    @DisplayName("concurrent reservations inside the window exhaust capacity")
    void concurrentReservationsExhaustCapacity() {
        List<RentalPeriod> existing = List.of(hours(8, 14), hours(10, 16));
        assertThat(calculator.peakUsage(hours(11, 12), existing)).isEqualTo(2);
        assertThat(calculator.canAccommodate(2, hours(11, 12), existing)).isFalse();
        assertThat(calculator.canAccommodate(3, hours(11, 12), existing)).isTrue();
    }

    @Test
    @DisplayName("overlap that happens only outside the request window is ignored")
    void overlapOutsideWindowIgnored() {
        List<RentalPeriod> existing = List.of(hours(6, 12), hours(9, 12));
        assertThat(calculator.canAccommodate(1, hours(12, 15), existing)).isTrue();
    }

    @Test
    @DisplayName("remaining reports free cars for the whole period")
    void remainingCars() {
        List<RentalPeriod> existing = List.of(hours(8, 14), hours(10, 16));
        assertThat(calculator.remaining(3, hours(9, 11), existing)).isEqualTo(1);
        assertThat(calculator.remaining(2, hours(11, 12), existing)).isZero();
        assertThat(calculator.remaining(1, hours(11, 12), existing)).isZero();
    }
}
