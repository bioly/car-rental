package com.example.carrental.web.api;

import com.example.carrental.fleet.persistence.CarType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body of {@code POST /api/v1/admin/cars}. Validated before it reaches the service.
 */
public record AddCarRequest(@NotBlank @Size(max = 16) String plate, @NotNull CarType type) {
}
