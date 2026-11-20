package com.example.carrental.booking;

import com.example.carrental.fleet.persistence.CarType;

import java.time.LocalDateTime;

/**
 * @param start          pick-up date and time in the rental location's timezone
 * @param idempotencyKey optional; a retry with the same key returns the original reservation instead of a second one
 */
public record ReserveCommand(long userId, CarType type, LocalDateTime start, int days, String idempotencyKey) {
}
