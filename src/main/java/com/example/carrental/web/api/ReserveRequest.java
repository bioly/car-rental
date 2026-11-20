package com.example.carrental.web.api;

import com.example.carrental.fleet.persistence.CarType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** {@code start} is the pick-up date and time in the rental location's timezone, e.g. {@code 2030-06-10T10:00}. */
public record ReserveRequest(@NotNull CarType type, @NotNull LocalDateTime start, @Min(1) int days) {
}
