package com.example.carrental.eligibility.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Simulated government registry: no network, deterministic, driven by the licence number prefix
 * so every failure mode can be demonstrated on demand.
 *
 * <pre>
 *   INV...    invalid licence                SHORT... valid, but expires in 10 days
 *   FAIL...   technical failure (5xx)        SLOW...  answers after 5s (exceeds the timeout)
 *   anything else: valid for 5 more years
 * </pre>
 *
 * A real integration replaces this bean (same name, {@code governmentLicenceClient}) with an HTTP client.
 */
@Component("governmentLicenceClient")
@ConditionalOnProperty(name = "app.licence.provider", havingValue = "fake", matchIfMissing = true)
@Slf4j
class FakeGovernmentLicenceClient implements LicenceVerifier {

    private static final Duration SLOW_RESPONSE = Duration.ofSeconds(5);

    private final Clock clock;
    private final Duration latency;

    FakeGovernmentLicenceClient(Clock clock, @Value("${app.licence.fake.latency:0ms}") Duration latency) {
        this.clock = clock;
        this.latency = latency;
    }

    @Override
    public LicenceRecord verify(String licenceNumber) {
        pause(latency);
        LocalDate today = LocalDate.now(clock);

        if (licenceNumber.startsWith("FAIL")) {
            throw new LicenceServiceException("Simulated upstream failure (HTTP 503)");
        }
        if (licenceNumber.startsWith("SLOW")) {
            pause(SLOW_RESPONSE);   // interrupted by the time limiter's cancellation
        }
        if (licenceNumber.startsWith("INV")) {
            return new LicenceRecord(LicenceStatus.INVALID, null);
        }
        if (licenceNumber.startsWith("SHORT")) {
            return new LicenceRecord(LicenceStatus.VALID, today.plusDays(10));
        }
        return new LicenceRecord(LicenceStatus.VALID, today.plusYears(5));
    }

    private static void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LicenceServiceException("Interrupted while waiting for the registry", e);
        }
    }
}
