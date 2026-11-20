package com.example.carrental.web.api;

import com.example.carrental.booking.BookingException;
import com.example.carrental.eligibility.registry.LicenceVerificationUnavailableException;
import com.example.carrental.fleet.FleetException;
import com.example.carrental.identity.IdentityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps domain exceptions to RFC 7807 problem responses. Messages are written for API clients and
 * never echo submitted values (no PII or secrets in error bodies); unexpected errors expose nothing.
 */
@RestControllerAdvice(basePackages = "com.example.carrental.web.api")
@Slf4j
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({BookingException.InvalidRequest.class, IllegalArgumentException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler({BookingException.NoAvailability.class, BookingException.CancellationNotAllowed.class,
            FleetException.CapacityCommitted.class, FleetException.DuplicatePlate.class,
            IdentityException.EmailAlreadyRegistered.class})
    ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(BookingException.NotEligible.class)
    ProblemDetail notEligible(BookingException.NotEligible e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Not eligible to rent", e.getMessage());
        problem.setProperty("reason", e.getCode());
        return problem;
    }

    @ExceptionHandler({BookingException.ReservationNotFound.class, FleetException.CarNotFound.class,
            IdentityException.UserNotFound.class})
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(BookingException.AccessDenied.class)
    ProblemDetail forbidden(BookingException.AccessDenied e) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", e.getMessage());
    }

    /** Fail closed: no reservation without a verified licence. The client may retry later. */
    @ExceptionHandler(LicenceVerificationUnavailableException.class)
    ResponseEntity<ProblemDetail> verificationUnavailable(LicenceVerificationUnavailableException e) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Licence check unavailable",
                "The driving licence could not be verified right now. Please try again shortly.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "30").body(problem);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "Unexpected error");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
