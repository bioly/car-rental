package com.example.carrental.booking;

import com.example.carrental.booking.availability.AvailabilityCalculator;
import com.example.carrental.booking.availability.RentalPeriod;
import com.example.carrental.booking.persistence.Reservation;
import com.example.carrental.booking.persistence.ReservationRepository;
import com.example.carrental.booking.persistence.ReservationStatus;
import com.example.carrental.fleet.CapacityGuard;
import com.example.carrental.fleet.persistence.CarType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Vetoes fleet shrinkage that would break promises already made to customers: removing a car is
 * allowed only if, at every future instant, the confirmed reservations still fit into the new capacity.
 * Reuses the same {@link AvailabilityCalculator} that guards new bookings.
 */
@Component
class ReservationCapacityGuard implements CapacityGuard {

    private final ReservationRepository reservations;
    private final Clock clock;
    private final AvailabilityCalculator calculator;

    ReservationCapacityGuard(ReservationRepository reservations, Clock clock, RentalProperties properties) {
        this.reservations = reservations;
        this.clock = clock;
        this.calculator = new AvailabilityCalculator();
    }

    @Override
    public boolean allows(CarType type, int newCapacity) {
        Instant now = clock.instant();
        List<RentalPeriod> commitments = reservations.findEndingAfter(type, ReservationStatus.CONFIRMED, now)
                .stream().map(Reservation::period).toList();
        if (commitments.isEmpty()) {
            return true;
        }
        Instant horizon = commitments.stream().map(RentalPeriod::end).max(Comparator.naturalOrder()).orElseThrow();
        return calculator.peakUsage(new RentalPeriod(now, horizon), commitments) <= newCapacity;
    }
}
