package com.example.carrental.web.api;

import com.example.carrental.identity.RegistrationService;
import com.example.carrental.identity.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for customer self-registration (public).
 */
@RestController
@RequestMapping("/api/v1/users")
class RegistrationApiController {

    private final RegistrationService registration;

    RegistrationApiController(RegistrationService registration) {
        this.registration = registration;
    }

    /** Public: self-registration of a customer. The response never contains the full licence number. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserView register(@Valid @RequestBody RegisterRequest request) {
        return registration.register(request.toCommand());
    }
}
