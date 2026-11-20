package com.example.carrental.eligibility.rules;

import com.example.carrental.eligibility.EligibilityContext;
import com.example.carrental.eligibility.EligibilityDecision;
import com.example.carrental.eligibility.EligibilityRequest;
import com.example.carrental.eligibility.EligibilityRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Period;

/** Every driver must be at least N years old on the pick-up date. */
@Component
@Order(10)
public class MinimumAgeRule implements EligibilityRule {

    private final int minimumAge;

    public MinimumAgeRule(@Value("${app.eligibility.min-age:21}") int minimumAge) {
        this.minimumAge = minimumAge;
    }

    @Override
    public EligibilityDecision evaluate(EligibilityContext context) {
        EligibilityRequest r = context.request();
        if (ageOnPickup(r) < minimumAge) {
            return EligibilityDecision.rejected("UNDERAGE", "Drivers must be at least " + minimumAge + " years old");
        }
        return EligibilityDecision.ok();
    }

    static int ageOnPickup(EligibilityRequest r) {
        return Period.between(r.dateOfBirth(), r.pickupDate()).getYears();
    }
}
