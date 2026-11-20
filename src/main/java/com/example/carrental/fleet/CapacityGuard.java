package com.example.carrental.fleet;

import com.example.carrental.fleet.persistence.CarType;

/**
 * Extension point owned by the fleet package: anyone holding commitments against fleet capacity
 * (today: the booking package) can veto a capacity reduction. Keeps the dependency pointing
 * booking -> fleet, never the other way round.
 */
public interface CapacityGuard {

    /** @return true if {@code newCapacity} cars of {@code type} can still honour every commitment */
    boolean allows(CarType type, int newCapacity);
}
