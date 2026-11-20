package com.example.carrental.web.ui;

import com.example.carrental.identity.IdentityException;
import com.example.carrental.identity.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Thymeleaf pages for logging in and registering. (The login itself is handled by Spring Security.)
 */
@Controller
class AuthController {

    private final RegistrationService registration;

    AuthController(RegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/register")
    String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result) {
        if (result.hasErrors()) {
            return "register";
        }
        try {
            registration.register(form.toCommand());
        } catch (IdentityException.EmailAlreadyRegistered e) {
            result.rejectValue("email", "duplicate", e.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
