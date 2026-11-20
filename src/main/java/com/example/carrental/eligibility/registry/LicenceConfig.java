package com.example.carrental.eligibility.registry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/** Wires the resilience decorator around whichever government client is active. */
@Configuration
class LicenceConfig {

    @Bean
    @Primary
    ResilientLicenceVerifier licenceVerifier(@Qualifier("governmentLicenceClient") LicenceVerifier governmentClient,
                                             ResilienceProperties properties, Clock clock) {
        return new ResilientLicenceVerifier(governmentClient, properties, clock);
    }
}
