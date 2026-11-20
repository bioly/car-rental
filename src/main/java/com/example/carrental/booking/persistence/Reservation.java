package com.example.carrental.booking.persistence;

import com.example.carrental.booking.availability.AvailabilityCalculator;
import com.example.carrental.booking.availability.RentalPeriod;
import com.example.carrental.fleet.persistence.CarType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A booking of one car of a type for a period. Deliberately not tied to a physical car:
 * capacity is checked per type, and the plate is assigned at pick-up (out of scope).
 * Refers to the customer by id only, so the booking package owns no foreign key into identity.
 */
@Entity
@Table(name = "reservation", uniqueConstraints = @UniqueConstraint(
        name = "uk_reservation_user_idempotency", columnNames = {"user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "car_type", nullable = false, length = 16)
    private CarType carType;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int days;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** Use {@link #confirmed} to create one: new reservations always start CONFIRMED. */
    private Reservation(long userId, CarType carType, RentalPeriod period, int days, BigDecimal totalPrice,
                        String idempotencyKey, Instant createdAt) {
        this.userId = userId;
        this.carType = carType;
        this.startsAt = period.start();
        this.endsAt = period.end();
        this.days = days;
        this.totalPrice = totalPrice;
        this.status = ReservationStatus.CONFIRMED;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public static Reservation confirmed(long userId, CarType carType, RentalPeriod period, int days, BigDecimal totalPrice,
                                 String idempotencyKey, Instant now) {
        return new Reservation(userId, carType, period, days, totalPrice, idempotencyKey, now);
    }

    /** Frees the car for other bookings; the row is kept as history. */
    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELLED;
    }

    /** The occupied interval, as used by {@link AvailabilityCalculator}. */
    public RentalPeriod period() {
        return new RentalPeriod(startsAt, endsAt);
    }
}
