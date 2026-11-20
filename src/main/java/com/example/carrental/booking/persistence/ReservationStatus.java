package com.example.carrental.booking.persistence;

/**
 * Lifecycle of a reservation. A cancelled reservation stays in the table (history) but no longer holds a car.
 */
public enum ReservationStatus {
    CONFIRMED,
    CANCELLED
}
