package com.example.carrental.booking;

import com.example.carrental.IntegrationTest;
import com.example.carrental.TestSupport;
import com.example.carrental.booking.persistence.ReservationStatus;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The functional requirements, one test each, through the real booking service (in-memory database):
 *
 * <ol>
 *   <li>A car of a given type can be reserved at a date and time for a number of days.</li>
 *   <li>There are three types of car: Sedan, SUV and Van.</li>
 *   <li>The number of cars of each type is limited.</li>
 * </ol>
 *
 * Each test books in its own date range, so tests do not influence each other.
 */
@IntegrationTest
class CarRentalRequirementsTest {

    @Autowired
    BookingService booking;
    @Autowired
    RegistrationService registration;
    @Autowired
    FleetService fleet;

    // ---- 1. reserve a car type at a date and time for a number of days -------------------------

    @Test
    void aCarOfAGivenTypeCanBeReservedAtADateAndTimeForANumberOfDays() {
        TestSupport.ensureCars(fleet, CarType.SEDAN, 1);
        LocalDateTime pickup = TestSupport.pickup(400);

        ReservationView reservation = reserve(CarType.SEDAN, pickup, 3);

        assertThat(reservation.type()).isEqualTo(CarType.SEDAN);
        assertThat(reservation.start()).isEqualTo(pickup);
        assertThat(reservation.days()).isEqualTo(3);
        assertThat(reservation.end()).isEqualTo(pickup.plusDays(3));
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.totalPrice()).isEqualByComparingTo("120.00");   // 3 days x 40.00
    }

    @Test
    void aReservationNeedsAFuturePickupAndAtLeastOneDay() {
        TestSupport.ensureCars(fleet, CarType.SEDAN, 1);

        assertThatThrownBy(() -> reserve(CarType.SEDAN, LocalDateTime.now().minusDays(1), 2))
                .isInstanceOf(BookingException.InvalidRequest.class);
        assertThatThrownBy(() -> reserve(CarType.SEDAN, TestSupport.pickup(401), 0))
                .isInstanceOf(BookingException.InvalidRequest.class);
    }

    // ---- 2. three types of car ------------------------------------------------------------------

    @Test
    void thereAreThreeTypesOfCar() {
        List<TypeAvailability> offer = booking.search(TestSupport.pickup(405), 2, null);

        assertThat(offer).extracting(TypeAvailability::type)
                .containsExactly(CarType.SEDAN, CarType.SUV, CarType.VAN);
    }

    // ---- 3. the number of cars of each type is limited ------------------------------------------

    @Test
    void onlyAsManyReservationsSucceedAsThereAreCarsOfThatType() {
        TestSupport.ensureCars(fleet, CarType.SUV, 1);
        int suvs = fleet.capacityOf(CarType.SUV);
        LocalDateTime pickup = TestSupport.pickup(410);

        for (int i = 0; i < suvs; i++) {
            reserve(CarType.SUV, pickup, 2);
        }

        assertThatThrownBy(() -> reserve(CarType.SUV, pickup, 2))
                .isInstanceOf(BookingException.NoAvailability.class);
    }

    @Test
    void eachTypeHasItsOwnStock() {
        TestSupport.ensureCars(fleet, CarType.SUV, 1);
        TestSupport.ensureCars(fleet, CarType.SEDAN, 1);
        LocalDateTime pickup = TestSupport.pickup(420);
        for (int i = 0; i < fleet.capacityOf(CarType.SUV); i++) {
            reserve(CarType.SUV, pickup, 2);
        }

        ReservationView sedan = reserve(CarType.SEDAN, pickup, 2);   // SUVs are sold out, sedans are not

        assertThat(sedan.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void cancellingAReservationReleasesTheCar() {
        TestSupport.ensureCars(fleet, CarType.SUV, 1);
        LocalDateTime pickup = TestSupport.pickup(430);
        long userId = TestSupport.registerAdult(registration).id();
        ReservationView last = null;
        for (int i = 0; i < fleet.capacityOf(CarType.SUV); i++) {
            last = booking.reserve(new ReserveCommand(userId, CarType.SUV, pickup, 2, null));
        }
        assertThatThrownBy(() -> reserve(CarType.SUV, pickup, 2)).isInstanceOf(BookingException.NoAvailability.class);

        booking.cancel(userId, last.id());

        assertThat(reserve(CarType.SUV, pickup, 2).status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void aCarReturnedAtTheMomentAnotherRentalStartsIsAvailable() {
        TestSupport.ensureCars(fleet, CarType.SUV, 1);
        LocalDateTime pickup = TestSupport.pickup(440);
        for (int i = 0; i < fleet.capacityOf(CarType.SUV); i++) {
            reserve(CarType.SUV, pickup, 2);            // every SUV is out for days 0-2
        }

        ReservationView next = reserve(CarType.SUV, pickup.plusDays(2), 1);   // starts when they come back

        assertThat(next.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void aRentalThatOverlapsWhileEveryCarIsOutIsRefused() {
        TestSupport.ensureCars(fleet, CarType.SUV, 1);
        LocalDateTime pickup = TestSupport.pickup(450);
        for (int i = 0; i < fleet.capacityOf(CarType.SUV); i++) {
            reserve(CarType.SUV, pickup, 3);            // out for days 0-3
        }

        assertThatThrownBy(() -> reserve(CarType.SUV, pickup.plusDays(2), 3))   // days 2-5 overlaps day 2-3
                .isInstanceOf(BookingException.NoAvailability.class);
    }

    private ReservationView reserve(CarType type, LocalDateTime pickup, int days) {
        long userId = TestSupport.registerAdult(registration).id();
        return booking.reserve(new ReserveCommand(userId, type, pickup, days, null));
    }
}
