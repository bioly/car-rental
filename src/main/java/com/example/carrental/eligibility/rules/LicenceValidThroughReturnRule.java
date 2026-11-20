package com.example.carrental.eligibility.rules;

import com.example.carrental.eligibility.EligibilityContext;
import com.example.carrental.eligibility.EligibilityDecision;
import com.example.carrental.eligibility.EligibilityRule;
import com.example.carrental.eligibility.registry.LicenceRecord;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The licence must still be valid on the day the car is returned, not just on pick-up. */
@Component
@Order(40)
public class LicenceValidThroughReturnRule implements EligibilityRule {

    @Override
    public EligibilityDecision evaluate(EligibilityContext context) {
        LicenceRecord licence = context.licence();
        if (licence.expiryDate() == null || licence.expiryDate().isBefore(context.request().returnDate())) {
            return EligibilityDecision.rejected("LICENCE_EXPIRES_BEFORE_RETURN",
                    "Your driving licence expires before the end of the rental");
        }
        return EligibilityDecision.ok();
    }
}
