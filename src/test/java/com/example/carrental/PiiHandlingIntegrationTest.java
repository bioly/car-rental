package com.example.carrental;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.carrental.booking.BookingService;
import com.example.carrental.booking.ReservationView;
import com.example.carrental.booking.ReserveCommand;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.RegistrationService;
import com.example.carrental.identity.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** PII rules, verified against the running application: password hashed, personal data absent from logs. */
@IntegrationTest
class PiiHandlingIntegrationTest {

    @Autowired
    RegistrationService registration;
    @Autowired
    BookingService booking;
    @Autowired
    FleetService fleet;
    @Autowired
    JdbcTemplate jdbc;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        rootLogger.detachAppender(logs);
    }

    @Test
    void thePasswordIsStoredAsAOneWayHash() {
        UserView user = TestSupport.registerAdult(registration);

        String stored = jdbc.queryForObject("select password_hash from app_user where id = ?", String.class, user.id());

        assertThat(stored).startsWith("$2").doesNotContain(TestSupport.PASSWORD);   // BCrypt
    }

    @Test
    void aFullBookingJourneyLogsNoPersonalData() {
        TestSupport.ensureCars(fleet, CarType.SEDAN, 1);
        UserView user = TestSupport.register(registration, "PII99999992", TestSupport.dobForAge(40));

        booking.search(TestSupport.pickup(120), 2, null);
        ReservationView reservation = booking.reserve(
                new ReserveCommand(user.id(), CarType.SEDAN, TestSupport.pickup(120), 2, null));
        booking.cancel(user.id(), reservation.id());

        List<String> messages = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).as("something must have been logged").isNotEmpty();
        assertThat(messages).noneMatch(m -> m.contains("PII99999992"));
        assertThat(messages).noneMatch(m -> m.contains(user.email()));
        assertThat(messages).noneMatch(m -> m.contains(TestSupport.PASSWORD));
    }
}
