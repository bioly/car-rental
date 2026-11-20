package com.example.carrental.eligibility.rules;

import com.example.carrental.eligibility.EligibilityContext;
import com.example.carrental.eligibility.EligibilityDecision;
import com.example.carrental.eligibility.EligibilityRule;
import com.example.carrental.eligibility.registry.LicenceStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The government registry must confirm the licence is valid. This rule triggers the external call. */
@Component
@Order(30)
public class LicenceStatusRule implements EligibilityRule {

    @Override
    public EligibilityDecision evaluate(EligibilityContext context) {
        LicenceStatus status = context.licence().status();
        if (status == LicenceStatus.VALID) {
            return EligibilityDecision.ok();
        }
        return EligibilityDecision.rejected("LICENCE_INVALID", "Your driving licence is not valid");
    }
}
