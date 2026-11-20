package com.example.carrental.eligibility;

import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.privacy.Masking;

import java.time.LocalDate;

/** Everything needed to decide whether a driver may rent a car type for a given period. */
public record EligibilityRequest(String licenceNumber, LocalDate dateOfBirth, CarType carType,
                                 LocalDate pickupDate, LocalDate returnDate) {

    @Override
    public String toString() {
        return "EligibilityRequest[licence=" + Masking.licence(licenceNumber)
                + ", carType=" + carType + ", pickup=" + pickupDate + ", return=" + returnDate + "]";
    }
}
