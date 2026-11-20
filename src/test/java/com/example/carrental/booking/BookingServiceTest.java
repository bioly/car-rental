package com.example.carrental.booking;

import com.example.carrental.booking.availability.RentalPeriod;
import com.example.carrental.booking.persistence.Reservation;
import com.example.carrental.booking.persistence.ReservationRepository;
import com.example.carrental.booking.persistence.ReservationStatus;
import com.example.carrental.eligibility.EligibilityDecision;
import com.example.carrental.eligibility.EligibilityRequest;
import com.example.carrental.eligibility.RentalEligibility;
import com.example.carrental.eligibility.registry.LicenceVerificationUnavailableException;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.UserDirectoryService;
import com.example.carrental.identity.persistence.DriverProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final LocalDateTime START = LocalDateTime.of(2030, 6, 10, 10, 0);
    private static final long USER = 7L;

    @Mock
    ReservationRepository reservations;
    @Mock
    FleetService fleet;
    @Mock
    UserDirectoryService users;
    @Mock
    RentalEligibility eligibility;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(reservations, fleet, users, eligibility,
                Clock.fixed(NOW, ZoneOffset.UTC), new RentalProperties("UTC", 30),
                TransactionOperations.withoutTransaction());
    }

    // ---- reserve ---------------------------------------------------------------------------------

    @Test
    void reserveConfirmsAndPublishesWhenEligibleAndACarIsFree() {
        when(users.profileFor(USER)).thenReturn(driver());
        when(eligibility.assess(any())).thenReturn(EligibilityDecision.ok());
        when(fleet.lockAndGetCapacity(CarType.SEDAN)).thenReturn(1);
        when(reservations.findOverlapping(eq(CarType.SEDAN), eq(ReservationStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of());
        when(reservations.save(any(Reservation.class))).thenAnswer(inv -> withId(inv.getArgument(0), 42L));

        ReservationView view = service.reserve(new ReserveCommand(USER, CarType.SEDAN, START, 3, null));

        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(view.start()).isEqualTo(START);
        assertThat(view.end()).isEqualTo(START.plusDays(3));
        assertThat(view.totalPrice()).isEqualByComparingTo("120.00");   // 3 days x 40.00
    }

    @Test
    void reserveAsksEligibilityWithPickupAndReturnDates() {
        when(users.profileFor(USER)).thenReturn(driver());
        when(eligibility.assess(any())).thenReturn(EligibilityDecision.rejected("X", "no"));

        assertThatThrownBy(() -> service.reserve(new ReserveCommand(USER, CarType.VAN, START, 3, null)))
                .isInstanceOf(BookingException.NotEligible.class);

        ArgumentCaptor<EligibilityRequest> captor = ArgumentCaptor.forClass(EligibilityRequest.class);
        verify(eligibility).assess(captor.capture());
        assertThat(captor.getValue().carType()).isEqualTo(CarType.VAN);
        assertThat(captor.getValue().pickupDate()).isEqualTo(LocalDate.of(2030, 6, 10));
        assertThat(captor.getValue().returnDate()).isEqualTo(LocalDate.of(2030, 6, 13));
    }

    @Test
    void reserveRejectsIneligibleDriverWithoutTouchingInventory() {
        when(users.profileFor(USER)).thenReturn(driver());
        when(eligibility.assess(any())).thenReturn(EligibilityDecision.rejected("UNDERAGE_VAN", "Too young for a van"));

        assertThatThrownBy(() -> service.reserve(new ReserveCommand(USER, CarType.VAN, START, 2, null)))
                .isInstanceOfSatisfying(BookingException.NotEligible.class,
                        e -> assertThat(e.getCode()).isEqualTo("UNDERAGE_VAN"));

        verifyNoInteractions(fleet);
        verify(reservations, never()).save(any());
    }

    @Test
    void reserveFailsClosedWhenTheLicenceRegistryIsUnavailable() {
        when(users.profileFor(USER)).thenReturn(driver());
        when(eligibility.assess(any()))
                .thenThrow(new LicenceVerificationUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.reserve(new ReserveCommand(USER, CarType.SEDAN, START, 2, null)))
                .isInstanceOf(LicenceVerificationUnavailableException.class);

        verifyNoInteractions(fleet);
        verify(reservations, never()).save(any());
    }

    @Test
    void reserveFailsWhenEveryCarIsTakenForPartOfThePeriod() {
        when(users.profileFor(USER)).thenReturn(driver());
        when(eligibility.assess(any())).thenReturn(EligibilityDecision.ok());
        when(fleet.lockAndGetCapacity(CarType.SEDAN)).thenReturn(1);
        Reservation overlapping = Reservation.confirmed(99L, CarType.SEDAN, periodStartingAfterDays(2), 3,
                new BigDecimal("120.00"), null, NOW);
        when(reservations.findOverlapping(eq(CarType.SEDAN), eq(ReservationStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of(overlapping));

        assertThatThrownBy(() -> service.reserve(new ReserveCommand(USER, CarType.SEDAN, START, 3, null)))
                .isInstanceOf(BookingException.NoAvailability.class);

        verify(reservations, never()).save(any());
    }

    @Test
    void reserveReplaysAnIdempotentRetryWithoutAnyOtherWork() {
        Reservation original = withId(Reservation.confirmed(USER, CarType.VAN, periodStartingAfterDays(0), 3,
                new BigDecimal("255.00"), "key-1", NOW), 5L);
        when(reservations.findByUserIdAndIdempotencyKey(USER, "key-1")).thenReturn(Optional.of(original));

        ReservationView view = service.reserve(new ReserveCommand(USER, CarType.VAN, START, 3, "key-1"));

        assertThat(view.id()).isEqualTo(5L);
        verifyNoInteractions(eligibility, fleet, users);
    }

    @Test
    void reserveRejectsPickupInThePast() {
        LocalDateTime past = LocalDateTime.of(2029, 12, 31, 10, 0);

        assertThatThrownBy(() -> service.reserve(new ReserveCommand(USER, CarType.SEDAN, past, 2, null)))
                .isInstanceOf(BookingException.InvalidRequest.class);
        verifyNoInteractions(users, eligibility, fleet);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 31})
    void reserveRejectsRentalLengthOutsideTheAllowedRange(int days) {
        assertThatThrownBy(() -> service.reserve(new ReserveCommand(USER, CarType.SEDAN, START, days, null)))
                .isInstanceOf(BookingException.InvalidRequest.class);
    }

    // ---- search ----------------------------------------------------------------------------------

    @Test
    void searchReportsFreeCarsPerTypeAndPublishesWhichTypesWereAvailable() {
        when(fleet.capacityOf(CarType.SEDAN)).thenReturn(2);
        when(fleet.capacityOf(CarType.SUV)).thenReturn(1);
        when(fleet.capacityOf(CarType.VAN)).thenReturn(0);
        when(reservations.findOverlapping(any(), eq(ReservationStatus.CONFIRMED), any(), any())).thenReturn(List.of());

        List<TypeAvailability> result = service.search(START, 3, null);

        assertThat(result).extracting(TypeAvailability::type)
                .containsExactly(CarType.SEDAN, CarType.SUV, CarType.VAN);
        assertThat(result).extracting(TypeAvailability::available).containsExactly(2, 1, 0);
        assertThat(result.get(1).totalPrice()).isEqualByComparingTo("195.00");   // 3 x 65.00

    }

    // ---- cancel ----------------------------------------------------------------------------------

    @Test
    void cancelMarksTheReservationCancelled() {
        Reservation reservation = withId(Reservation.confirmed(USER, CarType.SEDAN, periodStartingAfterDays(0), 3,
                new BigDecimal("120.00"), null, NOW), 5L);
        when(reservations.findById(5L)).thenReturn(Optional.of(reservation));

        service.cancel(USER, 5L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelByAnotherUserIsDenied() {
        Reservation reservation = withId(Reservation.confirmed(USER, CarType.SEDAN, periodStartingAfterDays(0), 3,
                new BigDecimal("120.00"), null, NOW), 5L);
        when(reservations.findById(5L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(USER + 1, 5L)).isInstanceOf(BookingException.AccessDenied.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void cancelAfterPickupIsNotAllowed() {
        RentalPeriod started = RentalPeriod.of(LocalDateTime.of(2029, 12, 30, 10, 0).atZone(ZoneOffset.UTC), 3);
        Reservation reservation = withId(Reservation.confirmed(USER, CarType.SEDAN, started, 3,
                new BigDecimal("120.00"), null, NOW), 5L);
        when(reservations.findById(5L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.cancel(USER, 5L)).isInstanceOf(BookingException.CancellationNotAllowed.class);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static DriverProfile driver() {
        return new DriverProfile(USER, "Test Driver", LocalDate.of(1985, 4, 12), "DL12345678");
    }

    /** A 3-day period starting {@code days} days after the test's START. */
    private static RentalPeriod periodStartingAfterDays(int days) {
        return RentalPeriod.of(START.plusDays(days).atZone(ZoneOffset.UTC), 3);
    }

    private static Reservation withId(Reservation reservation, long id) {
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }
}
