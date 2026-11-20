package com.example.carrental.fleet;

import com.example.carrental.fleet.persistence.CarType;

/**
 * Read-only view of a car for the admin screens and API (never expose the entity itself).
 */
public record CarView(long id, String plate, CarType type, boolean active) {
}
