package com.example.carrental.web;

import com.example.carrental.IntegrationTest;
import com.example.carrental.TestSupport;
import com.example.carrental.booking.BookingService;
import com.example.carrental.booking.ReserveCommand;
import com.example.carrental.fleet.CarView;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.RegistrationService;
import com.example.carrental.identity.UserView;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AdminApiIntegrationTest {

    private static final String ADMIN = "admin@carrental.local";
    private static final String ADMIN_PASSWORD = "ChangeMe-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    RegistrationService registration;
    @Autowired
    FleetService fleet;
    @Autowired
    BookingService booking;

    // ---- access control --------------------------------------------------------------------------

    @Test
    void anonymousCallersAreUnauthorisedAndCustomersAreForbidden() throws Exception {
        UserView customer = TestSupport.registerAdult(registration);

        mvc.perform(get("/api/v1/admin/cars")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/cars").with(httpBasic(customer.email(), TestSupport.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ---- fleet -----------------------------------------------------------------------------------

    @Test
    void adminCanAddAndRemoveACar() throws Exception {
        String plate = "ZZ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String created = mvc.perform(post("/api/v1/admin/cars").with(httpBasic(ADMIN, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plate\":\"" + plate + "\",\"type\":\"SUV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SUV"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(get("/api/v1/admin/cars").with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(content().string(containsString(plate)));

        mvc.perform(delete("/api/v1/admin/cars/{id}", id).with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/cars/{id}", id).with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCarCannotBeRemovedWhileReservationsStillNeedIt() throws Exception {
        TestSupport.ensureCars(fleet, CarType.SUV, 1);
        UserView customer = TestSupport.registerAdult(registration);
        int cars = fleet.capacityOf(CarType.SUV);
        for (int i = 0; i < cars; i++) {   // book every SUV for the same window
            booking.reserve(new ReserveCommand(customer.id(), CarType.SUV, TestSupport.pickup(80), 3, null));
        }
        CarView someSuv = fleet.listCars().stream().filter(c -> c.type() == CarType.SUV).findFirst().orElseThrow();

        mvc.perform(delete("/api/v1/admin/cars/{id}", someSuv.id()).with(httpBasic(ADMIN, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }
}
