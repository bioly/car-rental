package com.example.carrental.web.api;

import com.example.carrental.booking.BookingException;
import com.example.carrental.booking.BookingService;
import com.example.carrental.booking.ReservationView;
import com.example.carrental.booking.ReserveCommand;
import com.example.carrental.booking.TypeAvailability;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.security.AppPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * REST API for searching availability and managing the signed-in customer's reservations.
 * The controller only translates HTTP to service calls; all rules live in {@code BookingService}.
 */
@RestController
@RequestMapping("/api/v1")
class BookingApiController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final BookingService booking;

    BookingApiController(BookingService booking) {
        this.booking = booking;
    }

    /** Public: availability and price per car type. {@code start} is local pick-up time, e.g. 2030-06-10T10:00. */
    @GetMapping("/availability")
    List<TypeAvailability> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam int days,
            @RequestParam(required = false) CarType type) {
        return booking.search(start, days, type);
    }

    /**
     * Reserves a car. Send an {@code Idempotency-Key} header (1-64 chars) to make retries safe: a repeated
     * request with the same key returns the original reservation instead of creating a second one.
     */
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    ReservationView reserve(@AuthenticationPrincipal AppPrincipal me,
                            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                            @Valid @RequestBody ReserveRequest request) {
        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BookingException.InvalidRequest(
                    "Idempotency-Key must be 1-64 characters: letters, digits, '-' or '_'");
        }
        return booking.reserve(new ReserveCommand(me.getId(), request.type(), request.start(),
                request.days(), idempotencyKey));
    }

    @GetMapping("/reservations")
    List<ReservationView> myReservations(@AuthenticationPrincipal AppPrincipal me) {
        return booking.reservationsOf(me.getId());
    }

    @DeleteMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal AppPrincipal me, @PathVariable long id) {
        booking.cancel(me.getId(), id);
    }
}
