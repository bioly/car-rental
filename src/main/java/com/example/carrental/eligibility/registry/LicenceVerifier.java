package com.example.carrental.eligibility.registry;

/**
 * Port to the government driver-licence registry. This is the extension point: a real
 * integration implements this interface (and registers as the bean "governmentLicenceClient");
 * the resilience wrapper and the eligibility rules do not change.
 */
public interface LicenceVerifier {

    /**
     * @return the registry's answer; business outcomes (expired, suspended, unknown) are returned, never thrown
     * @throws LicenceServiceException on a technical failure (network, 5xx, ...), which is what retries and the
     *                                 circuit breaker react to
     */
    LicenceRecord verify(String licenceNumber);
}
