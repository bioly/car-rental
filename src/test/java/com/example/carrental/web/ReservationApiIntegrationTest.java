package com.example.carrental.web;

import com.example.carrental.IntegrationTest;
import com.example.carrental.TestSupport;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.RegistrationService;
import com.example.carrental.identity.UserView;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through the REST API: registration, availability, reservation, cancellation and the failure modes. */
@IntegrationTest
class ReservationApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    RegistrationService registration;
    @Autowired
    FleetService fleet;

    private static String reserveBody(CarType type, LocalDateTime start, int days) {
        return """
                {"type":"%s","start":"%s","days":%d}""".formatted(type, start, days);
    }

    private ResultActions reserve(UserView user, CarType type, LocalDateTime start, int days) throws Exception {
        return mvc.perform(post("/api/v1/reservations")
                .with(httpBasic(user.email(), TestSupport.PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reserveBody(type, start, days)));
    }

    // ---- registration ----------------------------------------------------------------------------

    @Test
    void registrationReturnsAMaskedLicenceAndNeverThePasswordOrFullLicence() throws Exception {
        String email = "api-" + UUID.randomUUID() + "@test.example";

        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"Password123!","fullName":"Api Tester",
                         "dateOfBirth":"%s","licenceNumber":"DL12345678"}""".formatted(email, TestSupport.dobForAge(40))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedLicence").value("****5678"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.licenceNumber").doesNotExist());
    }

    // ---- availability ----------------------------------------------------------------------------

    @Test
    void availabilityIsPublicAndListsAllThreeTypes() throws Exception {
        mvc.perform(get("/api/v1/availability")
                        .param("start", TestSupport.pickup(10).toString())
                        .param("days", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("SEDAN"))
                .andExpect(jsonPath("$[1].type").value("SUV"))
                .andExpect(jsonPath("$[2].type").value("VAN"));
    }

    // ---- reservations ----------------------------------------------------------------------------

    @Test
    void reservingRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(reserveBody(CarType.SEDAN, TestSupport.pickup(20), 2)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sedansSellOutCancellationFreesACarAndOtherUsersCannotCancel() throws Exception {
        TestSupport.ensureCars(fleet, CarType.SEDAN, 1);
        UserView alice = TestSupport.registerAdult(registration);
        UserView bob = TestSupport.registerAdult(registration);
        LocalDateTime start = TestSupport.pickup(30);
        int cars = fleet.capacityOf(CarType.SEDAN);

        for (int i = 0; i < cars; i++) {
            reserve(alice, CarType.SEDAN, start, 2)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalPrice").value(80.0));   // 2 days x 40.00
        }

        reserve(bob, CarType.SEDAN, start, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));

        String listing = mvc.perform(get("/api/v1/reservations").with(httpBasic(alice.email(), TestSupport.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(cars)))
                .andReturn().getResponse().getContentAsString();
        long reservationId = ((Number) JsonPath.read(listing, "$[0].id")).longValue();

        mvc.perform(delete("/api/v1/reservations/{id}", reservationId)
                        .with(httpBasic(bob.email(), TestSupport.PASSWORD)))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/reservations/{id}", reservationId)
                        .with(httpBasic(alice.email(), TestSupport.PASSWORD)))
                .andExpect(status().isNoContent());

        reserve(bob, CarType.SEDAN, start, 2).andExpect(status().isCreated());
    }

    @Test
    void aRetryWithTheSameIdempotencyKeyReturnsTheOriginalReservation() throws Exception {
        TestSupport.ensureCars(fleet, CarType.SEDAN, 1);
        UserView user = TestSupport.registerAdult(registration);
        String body = reserveBody(CarType.SEDAN, TestSupport.pickup(40), 1);

        String first = mvc.perform(post("/api/v1/reservations")
                        .with(httpBasic(user.email(), TestSupport.PASSWORD))
                        .header("Idempotency-Key", "key-" + user.id())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/v1/reservations")
                        .with(httpBasic(user.email(), TestSupport.PASSWORD))
                        .header("Idempotency-Key", "key-" + user.id())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat((Number) JsonPath.read(second, "$.id"))
                .isEqualTo(JsonPath.<Number>read(first, "$.id"));
        mvc.perform(get("/api/v1/reservations").with(httpBasic(user.email(), TestSupport.PASSWORD)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- eligibility and registry failures -------------------------------------------------------

    @Test
    void aDriverTooYoungForAVanGets422WithAReasonCode() throws Exception {
        UserView young = TestSupport.register(registration, TestSupport.VALID_LICENCE, TestSupport.dobForAge(22));

        reserve(young, CarType.VAN, TestSupport.pickup(50), 2)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.reason").value("UNDERAGE_VAN"));
    }

    @Test
    void whenTheRegistryIsDownBookingFailsClosedWith503AndRetryAfter() throws Exception {
        UserView flaky = TestSupport.register(registration, "FAIL00000001", TestSupport.dobForAge(40));

        reserve(flaky, CarType.SEDAN, TestSupport.pickup(52), 2)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.title").value("Licence check unavailable"));

        mvc.perform(get("/api/v1/reservations").with(httpBasic(flaky.email(), TestSupport.PASSWORD)))
                .andExpect(jsonPath("$", hasSize(0)));   // nothing was booked
    }
}
