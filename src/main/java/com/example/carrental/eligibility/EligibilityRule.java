package com.example.carrental.eligibility;

/**
 * One business rule (Specification pattern). Rules are Spring beans ordered with {@code @Order};
 * adding a rule means adding a class, not touching the service (open/closed).
 */
public interface EligibilityRule {

    /** @return {@link EligibilityDecision#ok()} if satisfied, otherwise a rejection with a code and message */
    EligibilityDecision evaluate(EligibilityContext context);
}
