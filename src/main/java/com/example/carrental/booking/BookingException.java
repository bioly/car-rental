package com.example.carrental.booking;

import com.example.carrental.booking.persistence.Reservation;
import com.example.carrental.fleet.persistence.CarType;

/**
 * Business failures of the booking use cases. Each subclass maps to one HTTP status in the web layer
 * (see {@code ApiExceptionHandler}), so the domain code never needs to know about HTTP.
 */
public class BookingException extends RuntimeException {

    protected BookingException(String message) {
        super(message);
    }

    public static class InvalidRequest extends BookingException {
        public InvalidRequest(String message) {
            super(message);
        }
    }

    public static class NoAvailability extends BookingException {
        public NoAvailability(CarType type) {
            super("No " + type.getLabel() + " is available for the whole requested period");
        }
    }

    public static class NotEligible extends BookingException {
        private final String code;

        public NotEligible(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class ReservationNotFound extends BookingException {
        public ReservationNotFound(long id) {
            super("Reservation " + id + " not found");
        }
    }

    public static class AccessDenied extends BookingException {
        public AccessDenied() {
            super("This reservation belongs to another user");
        }
    }

    public static class CancellationNotAllowed extends BookingException {
        public CancellationNotAllowed() {
            super("A reservation cannot be cancelled after its pick-up time");
        }
    }
}
