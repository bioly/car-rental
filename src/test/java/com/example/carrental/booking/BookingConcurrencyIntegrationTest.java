package com.example.carrental.booking;

import com.example.carrental.IntegrationTest;
import com.example.carrental.TestSupport;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the per-type inventory lock: many users racing for the last cars never overbook. */
@IntegrationTest
class BookingConcurrencyIntegrationTest {

    @Autowired
    BookingService booking;
    @Autowired
    RegistrationService registration;
    @Autowired
    FleetService fleet;

    @Test
    void neverBooksMoreCarsThanExist() throws Exception {
        TestSupport.ensureCars(fleet, CarType.VAN, 1);
        int cars = fleet.capacityOf(CarType.VAN);
        int contenders = cars + 4;
        LocalDateTime start = TestSupport.pickup(200);
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            userIds.add(TestSupport.registerAdult(registration).id());
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch startingGun = new CountDownLatch(1);
        List<Future<Boolean>> outcomes = new ArrayList<>();
        for (long userId : userIds) {
            outcomes.add(pool.submit(() -> {
                startingGun.await();
                try {
                    booking.reserve(new ReserveCommand(userId, CarType.VAN, start, 2, null));
                    return true;
                } catch (BookingException.NoAvailability e) {
                    return false;
                }
            }));
        }
        startingGun.countDown();

        long successes = 0;
        for (Future<Boolean> outcome : outcomes) {
            if (outcome.get(60, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).as("successful bookings").isEqualTo(cars);
        assertThat(booking.search(start, 2, CarType.VAN).get(0).available()).isZero();
    }
}
