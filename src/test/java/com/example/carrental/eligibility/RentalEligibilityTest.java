package com.example.carrental.eligibility;

import com.example.carrental.eligibility.registry.LicenceRecord;
import com.example.carrental.eligibility.registry.LicenceStatus;
import com.example.carrental.eligibility.registry.LicenceVerificationUnavailableException;
import com.example.carrental.eligibility.registry.LicenceVerifier;
import com.example.carrental.eligibility.rules.LicenceStatusRule;
import com.example.carrental.eligibility.rules.LicenceValidThroughReturnRule;
import com.example.carrental.eligibility.rules.MinimumAgeRule;
import com.example.carrental.eligibility.rules.VanMinimumAgeRule;
import com.example.carrental.fleet.persistence.CarType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentalEligibilityTest {

    private static final LocalDate PICKUP = LocalDate.of(2030, 6, 10);
    private static final LocalDate RETURN = LocalDate.of(2030, 6, 13);

    @Mock
    LicenceVerifier verifier;

    private RentalEligibility eligibility;

    @BeforeEach
    void setUp() {
        // same order as the @Order values on the real beans
        eligibility = new RentalEligibility(verifier, List.of(
                new MinimumAgeRule(21), new VanMinimumAgeRule(25),
                new LicenceStatusRule(), new LicenceValidThroughReturnRule()));
    }

    private static EligibilityRequest request(CarType type, int age) {
        return new EligibilityRequest("DL12345678", PICKUP.minusYears(age), type, PICKUP, RETURN);
    }

    @Test
    void eligibleDriverIsAcceptedAndTheRegistryIsCalledExactlyOnce() {
        when(verifier.verify("DL12345678")).thenReturn(new LicenceRecord(LicenceStatus.VALID, LocalDate.of(2035, 1, 1)));

        EligibilityDecision decision = eligibility.assess(request(CarType.SEDAN, 40));

        assertThat(decision.eligible()).isTrue();
        verify(verifier, times(1)).verify("DL12345678");   // two rules read the record, one remote call
    }

    @Test
    void localRulesRejectWithoutCallingTheExternalRegistry() {
        EligibilityDecision decision = eligibility.assess(request(CarType.VAN, 22));

        assertThat(decision.code()).isEqualTo("UNDERAGE_VAN");
        verifyNoInteractions(verifier);
    }

    @Test
    void anUnavailableRegistryPropagatesSoTheCallerCanFailClosed() {
        when(verifier.verify("DL12345678"))
                .thenThrow(new LicenceVerificationUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> eligibility.assess(request(CarType.SEDAN, 40)))
                .isInstanceOf(LicenceVerificationUnavailableException.class);
    }
}
