package com.example.carrental.web;

import com.example.carrental.IntegrationTest;
import com.example.carrental.TestSupport;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.RegistrationService;
import com.example.carrental.identity.UserView;
import com.example.carrental.identity.persistence.Role;
import com.example.carrental.identity.security.AppPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;


import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Thymeleaf pages render, are protected correctly, and the main customer flow works. */
@IntegrationTest
class UiSmokeIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    RegistrationService registration;
    @Autowired
    FleetService fleet;

    @Test
    void publicPagesRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Rent a car in minutes")));
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void protectedPagesSendAnonymousVisitorsToTheLoginPage() throws Exception {
        mvc.perform(get("/bookings")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/admin/fleet")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void aCustomerCanBookAndSeeTheBookingInMyBookings() throws Exception {
        TestSupport.ensureCars(fleet, CarType.SEDAN, 1);
        UserView customer = TestSupport.registerAdult(registration);
        AppPrincipal principal = new AppPrincipal(customer.id(), customer.email(), "unused", true, Role.USER);

        mvc.perform(post("/bookings").with(user(principal)).with(csrf())
                        .param("type", "SEDAN")
                        .param("start", TestSupport.pickup(140).toString())
                        .param("days", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bookings"));

        mvc.perform(get("/bookings").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sedan")))
                .andExpect(content().string(containsString("CONFIRMED")));
    }
}
