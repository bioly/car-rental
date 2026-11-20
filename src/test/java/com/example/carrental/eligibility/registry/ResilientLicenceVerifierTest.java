package com.example.carrental.eligibility.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientLicenceVerifierTest {

    private static final LicenceRecord VALID = new LicenceRecord(LicenceStatus.VALID, LocalDate.of(2040, 1, 1));
    private static final LicenceRecord INVALID = new LicenceRecord(LicenceStatus.INVALID, null);

    @Mock
    LicenceVerifier delegate;

    private ResilientLicenceVerifier verifier;

    @AfterEach
    void tearDown() {
        if (verifier != null) {
            verifier.close();
        }
    }

    private ResilientLicenceVerifier verifierWith(Duration timeout, int attempts, int failureThreshold) {
        verifier = new ResilientLicenceVerifier(delegate, new ResilienceProperties(
                timeout, attempts, Duration.ofMillis(1), failureThreshold, Duration.ofSeconds(30), 2, 2),
                Clock.systemUTC());
        return verifier;
    }

    @Test
    void aTransientFailureIsRetriedAndTheSecondAttemptSucceeds() {
        when(delegate.verify("L1")).thenThrow(new LicenceServiceException("503")).thenReturn(VALID);

        LicenceRecord result = verifierWith(Duration.ofSeconds(1), 3, 5).verify("L1");

        assertThat(result).isEqualTo(VALID);
        verify(delegate, times(2)).verify("L1");
    }

    @Test
    void businessAnswersAreReturnedAndNeverRetried() {
        when(delegate.verify("L1")).thenReturn(INVALID);

        LicenceRecord result = verifierWith(Duration.ofSeconds(1), 3, 5).verify("L1");

        assertThat(result.status()).isEqualTo(LicenceStatus.INVALID);
        verify(delegate, times(1)).verify("L1");
    }

    @Test
    void whenRetriesAreExhaustedTheCallerSeesUnavailable() {
        when(delegate.verify("L1")).thenThrow(new LicenceServiceException("503"));

        ResilientLicenceVerifier subject = verifierWith(Duration.ofSeconds(1), 2, 5);

        assertThatThrownBy(() -> subject.verify("L1"))
                .isInstanceOf(LicenceVerificationUnavailableException.class)
                .hasCauseInstanceOf(LicenceServiceException.class);
        verify(delegate, times(2)).verify("L1");
    }

    @Test
    void theCircuitOpensAfterRepeatedFailuresAndThenFailsFastWithoutCallingTheRegistry() {
        when(delegate.verify(anyString())).thenThrow(new LicenceServiceException("503"));
        // 2 attempts per call, threshold of 4 consecutive failures: two failed verifications open the circuit
        ResilientLicenceVerifier subject = verifierWith(Duration.ofSeconds(1), 2, 4);

        assertThatThrownBy(() -> subject.verify("L1")).isInstanceOf(LicenceVerificationUnavailableException.class);
        assertThatThrownBy(() -> subject.verify("L2")).isInstanceOf(LicenceVerificationUnavailableException.class);
        assertThat(subject.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> subject.verify("L3")).isInstanceOf(LicenceVerificationUnavailableException.class);
        verify(delegate, times(4)).verify(anyString());   // the third verification never reached the delegate
    }

    @Test
    void aSlowRegistryIsCutOffByTheTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(delegate.verify(anyString())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return VALID;
        });
        ResilientLicenceVerifier subject = verifierWith(Duration.ofMillis(100), 1, 5);

        try {
            assertThatThrownBy(() -> subject.verify("L1"))
                    .isInstanceOf(LicenceVerificationUnavailableException.class)
                    .hasCauseInstanceOf(TimeoutException.class);
        } finally {
            release.countDown();
        }
    }
}
