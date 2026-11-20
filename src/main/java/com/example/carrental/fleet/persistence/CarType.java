package com.example.carrental.fleet.persistence;

import java.math.BigDecimal;

/** The three rentable car types, each with a flat daily rate (EUR). */
public enum CarType {

    SEDAN("Sedan", new BigDecimal("40.00")),
    SUV("SUV", new BigDecimal("65.00")),
    VAN("Van", new BigDecimal("85.00"));

    private final String label;
    private final BigDecimal dailyRate;

    CarType(String label, BigDecimal dailyRate) {
        this.label = label;
        this.dailyRate = dailyRate;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }
}
