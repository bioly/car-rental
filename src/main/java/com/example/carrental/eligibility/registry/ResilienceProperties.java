package com.example.carrental.eligibility.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Tuning for the call to the government registry. Defaults mirror application.yml. */
@ConfigurationProperties(prefix = "app.licence.resilience")
public record ResilienceProperties(
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("2") int maxAttempts,
        @DefaultValue("200ms") Duration retryBackoff,
        @DefaultValue("5") int failureThreshold,
        @DefaultValue("30s") Duration openDuration,
        @DefaultValue("4") int maxConcurrentCalls,
        @DefaultValue("10") int queueCapacity) {
}
