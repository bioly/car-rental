package com.example.carrental.fleet;

import com.example.carrental.fleet.persistence.Car;
import com.example.carrental.fleet.persistence.CarType;

/**
 * Business failures of fleet management; mapped to HTTP statuses in the web layer.
 */
public class FleetException extends RuntimeException {

    protected FleetException(String message) {
        super(message);
    }

    public static class CarNotFound extends FleetException {
        public CarNotFound(long id) {
            super("Car " + id + " not found");
        }
    }

    public static class DuplicatePlate extends FleetException {
        public DuplicatePlate(String plate) {
            super("A car with plate " + plate + " already exists");
        }
    }

    public static class CapacityCommitted extends FleetException {
        public CapacityCommitted(CarType type) {
            super("Cannot remove this " + type.getLabel()
                    + ": the remaining cars could not honour existing reservations");
        }
    }
}
