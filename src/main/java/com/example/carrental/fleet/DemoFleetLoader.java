package com.example.carrental.fleet;

import com.example.carrental.fleet.persistence.Car;
import com.example.carrental.fleet.persistence.CarRepository;
import com.example.carrental.fleet.persistence.CarType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * A small demo fleet, only when {@code app.demo-data} is true (the default, see {@code application.yml}; tests switch it off).
 * Only loaded into an empty fleet, so restarting against a persistent database never duplicates cars.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
@Order(3)
@Slf4j
class DemoFleetLoader implements ApplicationRunner {

    private final CarRepository cars;

    DemoFleetLoader(CarRepository cars) {
        this.cars = cars;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cars.count() > 0) {
            return;
        }
        seed(CarType.SEDAN, "SED", 3);
        seed(CarType.SUV, "SUV", 2);
        seed(CarType.VAN, "VAN", 1);
        log.info("Demo fleet loaded: 3 sedans, 2 SUVs, 1 van");
    }

    private void seed(CarType type, String prefix, int count) {
        for (int i = 1; i <= count; i++) {
            cars.save(new Car(prefix + "-" + (100 + i), type));
        }
    }
}
