package com.example.carrental.eligibility.rules;

import com.example.carrental.eligibility.EligibilityContext;
import com.example.carrental.eligibility.EligibilityDecision;
import com.example.carrental.eligibility.EligibilityRequest;
import com.example.carrental.eligibility.registry.LicenceRecord;
import com.example.carrental.eligibility.registry.LicenceStatus;
import com.example.carrental.fleet.persistence.CarType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityRulesTest {

    private static final LocalDate PICKUP = LocalDate.of(2030, 6, 10);
    private static final LocalDate RETURN = LocalDate.of(2030, 6, 13);
    private static final LicenceRecord VALID_LONG = new LicenceRecord(LicenceStatus.VALID, LocalDate.of(2035, 1, 1));

    private static EligibilityContext context(CarType type, LocalDate dateOfBirth, LicenceRecord licence) {
        return new EligibilityContext(new EligibilityRequest("DL12345678", dateOfBirth, type, PICKUP, RETURN),
                () -> licence);
    }

    // ---- minimum age -----------------------------------------------------------------------------

    @Test
    void minimumAgeAcceptsADriverWhoTurns21OnThePickupDay() {
        MinimumAgeRule rule = new MinimumAgeRule(21);
        assertThat(rule.evaluate(context(CarType.SEDAN, PICKUP.minusYears(21), VALID_LONG)).eligible()).isTrue();
    }

    @Test
    void minimumAgeRejectsADriverOneDayShortOf21() {
        MinimumAgeRule rule = new MinimumAgeRule(21);
        EligibilityDecision decision =
                rule.evaluate(context(CarType.SEDAN, PICKUP.minusYears(21).plusDays(1), VALID_LONG));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.code()).isEqualTo("UNDERAGE");
    }

    @Test
    void vansNeedAnOlderDriver() {
        VanMinimumAgeRule rule = new VanMinimumAgeRule(25);

        assertThat(rule.evaluate(context(CarType.VAN, PICKUP.minusYears(24), VALID_LONG)).code())
                .isEqualTo("UNDERAGE_VAN");
        assertThat(rule.evaluate(context(CarType.VAN, PICKUP.minusYears(25), VALID_LONG)).eligible()).isTrue();
    }

    @Test
    void theVanRuleDoesNotApplyToOtherTypes() {
        VanMinimumAgeRule rule = new VanMinimumAgeRule(25);

        assertThat(rule.evaluate(context(CarType.SEDAN, PICKUP.minusYears(22), VALID_LONG)).eligible()).isTrue();
        assertThat(rule.evaluate(context(CarType.SUV, PICKUP.minusYears(22), VALID_LONG)).eligible()).isTrue();
    }

    // ---- licence status --------------------------------------------------------------------------

    @Test
    void anInvalidLicenceIsRejected() {
        LicenceStatusRule rule = new LicenceStatusRule();

        assertThat(rule.evaluate(context(CarType.SEDAN, PICKUP.minusYears(40), new LicenceRecord(LicenceStatus.INVALID, null))).code())
                .isEqualTo("LICENCE_INVALID");
    }

    // ---- valid through return --------------------------------------------------------------------

    @Test
    void theLicenceMustStillBeValidOnTheReturnDay() {
        LicenceValidThroughReturnRule rule = new LicenceValidThroughReturnRule();
        LocalDate dob = PICKUP.minusYears(40);

        assertThat(rule.evaluate(context(CarType.SEDAN, dob, new LicenceRecord(LicenceStatus.VALID, RETURN))).eligible())
                .isTrue();
        assertThat(rule.evaluate(context(CarType.SEDAN, dob, new LicenceRecord(LicenceStatus.VALID, RETURN.minusDays(1)))).code())
                .isEqualTo("LICENCE_EXPIRES_BEFORE_RETURN");
        assertThat(rule.evaluate(context(CarType.SEDAN, dob, new LicenceRecord(LicenceStatus.VALID, null))).eligible())
                .isFalse();
    }

    // ---- context ---------------------------------------------------------------------------------

    @Test
    void theRegistryIsQueriedLazilyAndAtMostOnce() {
        AtomicInteger calls = new AtomicInteger();
        EligibilityContext context = new EligibilityContext(
                new EligibilityRequest("DL12345678", PICKUP.minusYears(40), CarType.SEDAN, PICKUP, RETURN),
                () -> {
                    calls.incrementAndGet();
                    return VALID_LONG;
                });

        assertThat(calls).hasValue(0);
        context.licence();
        context.licence();
        assertThat(calls).hasValue(1);
    }
}
