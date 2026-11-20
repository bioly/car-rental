package com.example.carrental.eligibility;

import com.example.carrental.eligibility.registry.LicenceVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs the rules in order and stops at the first rejection. Rules are ordered cheapest first,
 * so the external registry is only consulted when the local rules have passed.
 */
@Service
@Slf4j
public class RentalEligibility {

    private final LicenceVerifier verifier;
    private final List<EligibilityRule> rules;

    RentalEligibility(LicenceVerifier verifier, List<EligibilityRule> rules) {
        this.verifier = verifier;
        this.rules = rules;
    }

    /**
     * @throws LicenceVerificationUnavailableException if the government registry could not be consulted
     */
    public EligibilityDecision assess(EligibilityRequest request) {
        EligibilityContext context = new EligibilityContext(request, () -> verifier.verify(request.licenceNumber()));
        for (EligibilityRule rule : rules) {
            EligibilityDecision decision = rule.evaluate(context);
            if (!decision.eligible()) {
                log.info("Eligibility rejected rule={} code={}", rule.getClass().getSimpleName(), decision.code());
                return decision;
            }
        }
        return EligibilityDecision.ok();
    }
}
