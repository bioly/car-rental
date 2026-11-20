package com.example.carrental.booking;

import com.example.carrental.IntegrationTest;
import com.example.carrental.TestSupport;
import com.example.carrental.booking.availability.RentalPeriod;
import com.example.carrental.booking.persistence.Reservation;
import com.example.carrental.booking.persistence.ReservationRepository;
import com.example.carrental.booking.persistence.ReservationStatus;
import com.example.carrental.fleet.persistence.CarType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Query semantics of the repository against a real (in-memory) database. */
@IntegrationTest
class ReservationRepositoryIntegrationTest {

    private static final long USER = 9_000_001L;   // a user id no other test uses; results are filtered by it
    private static final BigDecimal PRICE = new BigDecimal("80.00");

    @Autowired
    ReservationRepository repository;

    @AfterEach
    void removeTestReservations() {   // the database is shared with other test classes: leave no commitments behind
        repository.deleteAll(repository.findByUserIdOrderByStartsAtDesc(USER));
    }

    private static RentalPeriod days(int fromDay, int toDay) {
        Instant base = TestSupport.pickup(300).toInstant(ZoneOffset.UTC);
        return new RentalPeriod(base.plusSeconds(fromDay * 86_400L), base.plusSeconds(toDay * 86_400L));
    }

    private List<Reservation> overlapping(CarType type, RentalPeriod window) {
        return repository.findOverlapping(type, ReservationStatus.CONFIRMED, window.start(), window.end())
                .stream().filter(r -> r.getUserId() == USER).toList();
    }

    @Test
    void findOverlappingReturnsOnlyConfirmedReservationsOfTheTypeThatIntersect() {
        Instant now = Instant.now();
        Reservation inWindow = repository.save(Reservation.confirmed(USER, CarType.SEDAN, days(0, 2), 2, PRICE, null, now));
        Reservation cancelled = repository.save(Reservation.confirmed(USER, CarType.SEDAN, days(5, 7), 2, PRICE, null, now));
        cancelled.cancel();
        repository.save(cancelled);
        Reservation otherType = repository.save(Reservation.confirmed(USER, CarType.VAN, days(0, 2), 2, PRICE, null, now));

        assertThat(overlapping(CarType.SEDAN, days(1, 3))).extracting(Reservation::getId)
                .containsExactly(inWindow.getId());
        assertThat(overlapping(CarType.SEDAN, days(4, 6))).isEmpty();                 // the only one there is cancelled
        assertThat(overlapping(CarType.VAN, days(0, 1))).extracting(Reservation::getId)
                .containsExactly(otherType.getId());
    }

    @Test
    void touchingIntervalsDoNotOverlap() {
        repository.save(Reservation.confirmed(USER, CarType.SUV, days(10, 12), 2, PRICE, null, Instant.now()));

        assertThat(overlapping(CarType.SUV, days(12, 14))).isEmpty();   // return day == next pick-up day
        assertThat(overlapping(CarType.SUV, days(8, 10))).isEmpty();
    }

    @Test
    void anIdempotencyKeyIsUniquePerUser() {
        Instant now = Instant.now();
        repository.saveAndFlush(Reservation.confirmed(USER, CarType.SEDAN, days(20, 21), 1, PRICE, "repo-key", now));

        assertThatThrownBy(() -> repository.saveAndFlush(
                Reservation.confirmed(USER, CarType.SEDAN, days(22, 23), 1, PRICE, "repo-key", now)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findByUserIdAndIdempotencyKey(USER, "repo-key")).isPresent();
    }
}
