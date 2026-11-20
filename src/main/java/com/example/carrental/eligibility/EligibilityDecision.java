package com.example.carrental.eligibility;

/** Outcome of the eligibility check. {@code code} is machine readable, {@code message} is for people. */
public record EligibilityDecision(boolean eligible, String code, String message) {

    private static final EligibilityDecision OK = new EligibilityDecision(true, "OK", "Eligible");

    public static EligibilityDecision ok() {
        return OK;
    }

    public static EligibilityDecision rejected(String code, String message) {
        return new EligibilityDecision(false, code, message);
    }
}
