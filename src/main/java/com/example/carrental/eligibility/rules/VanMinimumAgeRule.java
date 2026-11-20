package com.example.carrental.eligibility.rules;

import com.example.carrental.eligibility.EligibilityContext;
import com.example.carrental.eligibility.EligibilityDecision;
import com.example.carrental.eligibility.EligibilityRequest;
import com.example.carrental.eligibility.EligibilityRule;
import com.example.carrental.fleet.persistence.CarType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Vans need an older driver than other types. */
@Component
@Order(20)
public class VanMinimumAgeRule implements EligibilityRule {

    private final int minimumAge;

    public VanMinimumAgeRule(@Value("${app.eligibility.van-min-age:25}") int minimumAge) {
        this.minimumAge = minimumAge;
    }

    @Override
    public EligibilityDecision evaluate(EligibilityContext context) {
        EligibilityRequest r = context.request();
        if (r.carType() == CarType.VAN && MinimumAgeRule.ageOnPickup(r) < minimumAge) {
            return EligibilityDecision.rejected("UNDERAGE_VAN",
                    "Drivers must be at least " + minimumAge + " years old to rent a Van");
        }
        return EligibilityDecision.ok();
    }
}
