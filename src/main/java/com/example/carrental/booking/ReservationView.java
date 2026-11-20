package com.example.carrental.booking;

import com.example.carrental.booking.persistence.Reservation;
import com.example.carrental.booking.persistence.ReservationStatus;
import com.example.carrental.fleet.persistence.CarType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Start and end are expressed in the rental location's timezone. */
public record ReservationView(long id, CarType type, LocalDateTime start, LocalDateTime end,
                              int days, BigDecimal totalPrice, ReservationStatus status) {

    /** Converts the stored UTC instants of a reservation to local rental time for display. */
    public static ReservationView from(Reservation r, ZoneId zone) {
        return new ReservationView(r.getId(), r.getCarType(), LocalDateTime.ofInstant(r.getStartsAt(), zone),
                LocalDateTime.ofInstant(r.getEndsAt(), zone), r.getDays(), r.getTotalPrice(), r.getStatus());
    }
}
