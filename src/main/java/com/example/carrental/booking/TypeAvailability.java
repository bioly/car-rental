package com.example.carrental.booking;

import com.example.carrental.fleet.persistence.CarType;

import java.math.BigDecimal;

/**
 * Availability of one car type for a requested period.
 *
 * @param capacity   active cars of this type
 * @param available  cars free for the <em>whole</em> period
 * @param totalPrice price for the whole period (EUR)
 */
public record TypeAvailability(CarType type, int capacity, int available, BigDecimal totalPrice) {
}
