package com.example.carrental.eligibility.registry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A deliberately small circuit breaker (plain Java, no library).
 *
 * <pre>
 * CLOSED --N consecutive failures--> OPEN --open duration elapsed--> HALF_OPEN (one probe call)
 *    ^                                 ^                                |
 *    +------------- success -----------+------------- failure ----------+
 * </pre>
 *
 * Simplification vs. a library: it counts <em>consecutive</em> failures, not a failure rate over a sliding window.
 * All state changes are synchronized; the critical sections are a few field updates.
 */
final class CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean probeInFlight;

    CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    /** Ask before every call. False means: do not call, fail fast. */
    synchronized boolean tryAcquire() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (clock.instant().isBefore(openedAt.plus(openDuration))) {
                    return false;
                }
                state = State.HALF_OPEN;   // time is up: let exactly one probe through
                probeInFlight = true;
                return true;
            default:   // HALF_OPEN
                if (probeInFlight) {
                    return false;
                }
                probeInFlight = true;
                return true;
        }
    }

    synchronized void recordSuccess() {
        consecutiveFailures = 0;
        probeInFlight = false;
        state = State.CLOSED;
    }

    synchronized void recordFailure() {
        probeInFlight = false;
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }

    synchronized State state() {
        return state;
    }
}
