package com.example.carrental.fleet;

import com.example.carrental.fleet.persistence.CarCategory;
import com.example.carrental.fleet.persistence.CarCategoryRepository;
import com.example.carrental.fleet.persistence.CarType;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Makes sure there is one lock row per car type.
 *
 * <p>Required in every environment: the booking transaction locks the row of the car type ({@code SELECT ... FOR UPDATE})
 * to serialise bookings, so the rows must exist before the first booking. Idempotent.
 */
@Component
@Order(1)
class FleetBootstrap implements ApplicationRunner {

    private final CarCategoryRepository categories;

    FleetBootstrap(CarCategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (CarType type : CarType.values()) {
            if (!categories.existsById(type.name())) {
                categories.save(new CarCategory(type.name()));
            }
        }
    }
}
