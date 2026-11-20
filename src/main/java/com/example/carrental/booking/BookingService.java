package com.example.carrental.booking;

import com.example.carrental.booking.availability.AvailabilityCalculator;
import com.example.carrental.booking.availability.RentalPeriod;
import com.example.carrental.booking.persistence.Reservation;
import com.example.carrental.booking.persistence.ReservationRepository;
import com.example.carrental.booking.persistence.ReservationStatus;
import com.example.carrental.eligibility.EligibilityDecision;
import com.example.carrental.eligibility.EligibilityRequest;
import com.example.carrental.eligibility.RentalEligibility;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.UserDirectoryService;
import com.example.carrental.identity.persistence.DriverProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the booking use cases.
 *
 * <p>Reservation flow and why it is ordered this way:
 * <ol>
 *   <li>Validate and replay idempotent retries (cheap, no external calls).</li>
 *   <li>Check eligibility with the government registry: slow and unreliable, so it runs
 *       <em>before</em> the transaction; no database lock is ever held while waiting on the network.</li>
 *   <li>One short transaction: lock the car type, check availability, insert the reservation.</li>
 * </ol>
 */
@Service
@Slf4j
public class BookingService {

    private final ReservationRepository reservations;
    private final FleetService fleet;
    private final UserDirectoryService users;
    private final RentalEligibility eligibility;
    private final Clock clock;
    private final RentalProperties properties;
    private final TransactionOperations tx;
    private final AvailabilityCalculator calculator;
    private final ZoneId zone;

    BookingService(ReservationRepository reservations, FleetService fleet, UserDirectoryService users,
                          RentalEligibility eligibility, Clock clock,
                          RentalProperties properties, TransactionOperations tx) {
        this.reservations = reservations;
        this.fleet = fleet;
        this.users = users;
        this.eligibility = eligibility;
        this.clock = clock;
        this.properties = properties;
        this.tx = tx;
        this.calculator = new AvailabilityCalculator();
        this.zone = properties.zoneId();
    }

    /**
     * Availability and price per car type for a rental starting at {@code start} for {@code days} days.
     *
     * @param type optional filter; {@code null} means all types
     * @throws BookingException.InvalidRequest bad dates
     */
    public List<TypeAvailability> search(LocalDateTime start, int days, CarType type) {
        RentalPeriod period = periodFor(start, days);
        List<CarType> types = type == null ? List.of(CarType.values()) : List.of(type);

        List<TypeAvailability> result = types.stream()
                .map(t -> availabilityOf(t, period, days))
                .toList();

        return result;
    }

    /**
     * Verifies the driver's eligibility (government registry), then books a car of the type if one is free
     * for the whole period.
     *
     * @throws BookingException.InvalidRequest       bad dates
     * @throws BookingException.NotEligible          the driver may not rent this car
     * @throws BookingException.NoAvailability       every car of this type is taken for part of the period
     * @throws LicenceVerificationUnavailableException the registry could not be consulted (fail closed)
     */
    public ReservationView reserve(ReserveCommand command) {
        RentalPeriod period = periodFor(command.start(), command.days());

        // 1. A retry with the same Idempotency-Key returns the original reservation instead of booking twice.
        Optional<ReservationView> replay = findReplay(command);
        if (replay.isPresent()) {
            log.info("Idempotent replay reservationId={}", replay.get().id());
            return replay.get();
        }

        // 2. Eligibility first. The registry call is slow and unreliable, so no transaction or lock is open yet.
        DriverProfile driver = users.profileFor(command.userId());
        LocalDate pickupDate = command.start().toLocalDate();
        LocalDate returnDate = period.end().atZone(zone).toLocalDate();
        EligibilityDecision decision = eligibility.assess(new EligibilityRequest(
                driver.licenceNumber(), driver.dateOfBirth(), command.type(), pickupDate, returnDate));
        if (!decision.eligible()) {
            throw new BookingException.NotEligible(decision.code(), decision.message());
        }

        // 3. One short transaction: lock the car type, check availability, save the reservation.
        try {
            Reservation saved = tx.execute(status -> book(command, period));
            return ReservationView.from(saved, zone);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests with the same idempotency key: the unique constraint picked a winner.
            return findReplay(command).orElseThrow(() -> e);
        }
    }

    public List<ReservationView> reservationsOf(long userId) {
        return reservations.findByUserIdOrderByStartsAtDesc(userId).stream().map(r -> ReservationView.from(r, zone)).toList();
    }

    /**
     * @throws BookingException.ReservationNotFound     unknown reservation
     * @throws BookingException.AccessDenied            not the owner
     * @throws BookingException.CancellationNotAllowed  pick-up time has passed
     */
    public void cancel(long userId, long reservationId) {
        tx.executeWithoutResult(status -> {
            Reservation reservation = reservations.findById(reservationId)
                    .orElseThrow(() -> new BookingException.ReservationNotFound(reservationId));
            if (reservation.getUserId() != userId) {
                throw new BookingException.AccessDenied();
            }
            if (reservation.isCancelled()) {
                return;   // cancelling twice is harmless
            }
            if (!reservation.getStartsAt().isAfter(clock.instant())) {
                throw new BookingException.CancellationNotAllowed();
            }
            reservation.cancel();
            log.info("Reservation cancelled id={} userId={}", reservationId, userId);
        });
    }

    /** The critical section: runs inside the transaction, under the per-type inventory lock. */
    private Reservation book(ReserveCommand command, RentalPeriod period) {
        int capacity = fleet.lockAndGetCapacity(command.type());
        List<RentalPeriod> existing = overlapping(command.type(), period);
        if (!calculator.canAccommodate(capacity, period, existing)) {
            throw new BookingException.NoAvailability(command.type());
        }
        Reservation reservation = reservations.save(Reservation.confirmed(
                command.userId(), command.type(), period, command.days(),
                priceFor(command.type(), command.days()), command.idempotencyKey(), clock.instant()));
        log.info("Reservation confirmed id={} type={} userId={} days={}",
                reservation.getId(), command.type(), command.userId(), command.days());
        return reservation;
    }

    private TypeAvailability availabilityOf(CarType type, RentalPeriod period, int days) {
        int capacity = fleet.capacityOf(type);
        int free = calculator.remaining(capacity, period, overlapping(type, period));
        return new TypeAvailability(type, capacity, free, priceFor(type, days));
    }

    private List<RentalPeriod> overlapping(CarType type, RentalPeriod period) {
        return reservations.findOverlapping(type, ReservationStatus.CONFIRMED, period.start(), period.end())
                .stream().map(Reservation::period).toList();
    }

    private Optional<ReservationView> findReplay(ReserveCommand command) {
        if (command.idempotencyKey() == null) {
            return Optional.empty();
        }
        return reservations.findByUserIdAndIdempotencyKey(command.userId(), command.idempotencyKey())
                .map(r -> ReservationView.from(r, zone));
    }

    private RentalPeriod periodFor(LocalDateTime start, int days) {
        if (start == null) {
            throw new BookingException.InvalidRequest("Pick-up date and time are required");
        }
        if (days < 1 || days > properties.maxDays()) {
            throw new BookingException.InvalidRequest("Rental length must be between 1 and " + properties.maxDays() + " days");
        }
        ZonedDateTime zonedStart = start.atZone(zone);
        if (zonedStart.toInstant().isBefore(clock.instant())) {
            throw new BookingException.InvalidRequest("Pick-up must be in the future");
        }
        return RentalPeriod.of(zonedStart, days);
    }

    private static BigDecimal priceFor(CarType type, int days) {
        return type.getDailyRate().multiply(BigDecimal.valueOf(days));
    }
}
