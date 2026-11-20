package com.example.carrental.eligibility.registry;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30), clock);

    private void failTimes(int n) {
        for (int i = 0; i < n; i++) {
            assertThat(breaker.tryAcquire()).isTrue();
            breaker.recordFailure();
        }
    }

    @Test
    void staysClosedBelowTheThresholdAndASuccessResetsTheCount() {
        failTimes(2);
        breaker.recordSuccess();
        failTimes(2);

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void opensAfterTheThresholdAndRejectsCalls() {
        failTimes(3);

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isFalse();
    }

    @Test
    void afterTheOpenDurationExactlyOneProbeIsLetThrough() {
        failTimes(3);
        clock.advance(Duration.ofSeconds(31));

        assertThat(breaker.tryAcquire()).isTrue();    // the probe
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.tryAcquire()).isFalse();   // everyone else still waits
    }

    @Test
    void aSuccessfulProbeClosesTheCircuit() {
        failTimes(3);
        clock.advance(Duration.ofSeconds(31));
        breaker.tryAcquire();

        breaker.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isTrue();
    }

    @Test
    void aFailedProbeReopensTheCircuitForAnotherFullPeriod() {
        failTimes(3);
        clock.advance(Duration.ofSeconds(31));
        breaker.tryAcquire();

        breaker.recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        clock.advance(Duration.ofSeconds(10));
        assertThat(breaker.tryAcquire()).isFalse();
    }
}
