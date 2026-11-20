package com.example.carrental.booking.persistence;

import com.example.carrental.fleet.persistence.CarType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for reservations. Only the booking feature queries reservations.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservations of the given status whose interval intersects {@code [from, to)}. */
    @Query("select r from Reservation r where r.carType = :type and r.status = :status "
            + "and r.startsAt < :to and r.endsAt > :from")
    List<Reservation> findOverlapping(@Param("type") CarType type, @Param("status") ReservationStatus status,
                                      @Param("from") Instant from, @Param("to") Instant to);

    @Query("select r from Reservation r where r.carType = :type and r.status = :status and r.endsAt > :from")
    /** Confirmed reservations of a type that have not ended yet. Used to check that a fleet reduction is safe. */
    List<Reservation> findEndingAfter(@Param("type") CarType type, @Param("status") ReservationStatus status,
                                      @Param("from") Instant from);

    /** A customer's reservations, newest pick-up first. */
    List<Reservation> findByUserIdOrderByStartsAtDesc(long userId);

    /** Finds the reservation created by an earlier request with the same idempotency key (safe retries). */
    Optional<Reservation> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);
}
