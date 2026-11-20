package com.example.carrental.eligibility;

import com.example.carrental.eligibility.registry.LicenceRecord;
import java.util.function.Supplier;

/**
 * Input to the eligibility rules. The government record is fetched lazily and at most once, so
 * cheap local rules (age) can reject a request without ever calling the external registry.
 */
public final class EligibilityContext {

    private final EligibilityRequest request;
    private final Supplier<LicenceRecord> registryLookup;
    private LicenceRecord record;

    public EligibilityContext(EligibilityRequest request, Supplier<LicenceRecord> registryLookup) {
        this.request = request;
        this.registryLookup = registryLookup;
    }

    public EligibilityRequest request() {
        return request;
    }

    public LicenceRecord licence() {
        if (record == null) {
            record = registryLookup.get();
        }
        return record;
    }
}
