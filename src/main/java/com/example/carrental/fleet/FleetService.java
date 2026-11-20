package com.example.carrental.fleet;

import com.example.carrental.fleet.persistence.Car;
import com.example.carrental.fleet.persistence.CarCategoryRepository;
import com.example.carrental.fleet.persistence.CarRepository;
import com.example.carrental.fleet.persistence.CarType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Fleet management and the inventory lock.
 *
 * <p>Besides adding and removing cars, this service owns the per-type lock that serialises bookings:
 * {@link #lockAndGetCapacity(CarType)} is called by the booking transaction before it checks availability.
 */
@Service
@Slf4j
public class FleetService {

    private final CarRepository cars;
    private final CarCategoryRepository categories;
    private final List<CapacityGuard> guards;

    FleetService(CarRepository cars, CarCategoryRepository categories, List<CapacityGuard> guards) {
        this.cars = cars;
        this.categories = categories;
        this.guards = guards;
    }

    /** Number of active cars of the given type. */
    @Transactional(readOnly = true)
    public int capacityOf(CarType type) {
        return (int) cars.countByCarTypeAndActiveTrue(type);
    }

    /**
     * Takes the inventory lock for this car type until the current transaction ends, then returns
     * its capacity. Serialises concurrent bookings (and fleet changes) per car type, so the
     * "is there a free car?" check and the insert of a reservation cannot interleave.
     * Must be called inside a transaction.
     *
     * <p>Microservice note: in a split, this lock would be replaced by a booking-owned inventory
     * counter or a distributed lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int lockAndGetCapacity(CarType type) {
        lock(type);
        return capacityOf(type);
    }

    @Transactional(readOnly = true)
    public List<CarView> listCars() {
        return cars.findByActiveTrueOrderByCarTypeAscPlateAsc().stream().map(FleetService::toView).toList();
    }

    /** @throws FleetException.DuplicatePlate if an active car already has this plate */
    @Transactional
    public CarView addCar(String plate, CarType type) {
        String normalised = plate.trim().toUpperCase(Locale.ROOT);
        lock(type);
        if (cars.existsByPlateAndActiveTrue(normalised)) {
            throw new FleetException.DuplicatePlate(normalised);
        }
        Car saved = cars.save(new Car(normalised, type));
        log.info("Car added id={} type={}", saved.getId(), type);
        return toView(saved);
    }

    /**
     * Soft-deletes a car.
     *
     * @throws FleetException.CarNotFound         if there is no such active car
     * @throws FleetException.CapacityCommitted   if the remaining cars could not honour existing reservations
     */
    @Transactional
    public void removeCar(long id) {
        Car car = cars.findById(id)
                .filter(Car::isActive)
                .orElseThrow(() -> new FleetException.CarNotFound(id));
        CarType type = car.getCarType();
        lock(type);   // same lock as bookings: no booking can slip in between the check and the removal
        int newCapacity = capacityOf(type) - 1;
        for (CapacityGuard guard : guards) {
            if (!guard.allows(type, newCapacity)) {
                log.info("Car removal refused id={} type={} newCapacity={}", id, type, newCapacity);
                throw new FleetException.CapacityCommitted(type);
            }
        }
        car.deactivate();
        log.info("Car removed id={} type={}", id, type);
    }

    private void lock(CarType type) {
        categories.findForUpdate(type.name())
                .orElseThrow(() -> new IllegalStateException("Car category not initialised: " + type));
    }

    private static CarView toView(Car car) {
        return new CarView(car.getId(), car.getPlate(), car.getCarType(), car.isActive());
    }
}
