package com.example.carrental.eligibility.registry;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FakeGovernmentLicenceClientTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 1);

    private final FakeGovernmentLicenceClient client = new FakeGovernmentLicenceClient(CLOCK, Duration.ZERO);

    @Test
    void prefixesSelectTheSimulatedOutcome() {
        assertThat(client.verify("INV00000001")).isEqualTo(new LicenceRecord(LicenceStatus.INVALID, null));
        assertThat(client.verify("SHORT000001")).isEqualTo(new LicenceRecord(LicenceStatus.VALID, TODAY.plusDays(10)));
    }

}
