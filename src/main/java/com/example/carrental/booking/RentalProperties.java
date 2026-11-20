package com.example.carrental.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * @param zone       timezone of the single rental location; pick-up times are interpreted in it
 * @param maxDays    longest rental
 */
@ConfigurationProperties(prefix = "app.rental")
public record RentalProperties(
        @DefaultValue("UTC") String zone,
        @DefaultValue("30") int maxDays) {

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
