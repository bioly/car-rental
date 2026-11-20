package com.example.carrental.web.api;

import com.example.carrental.fleet.CarView;
import com.example.carrental.fleet.FleetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Everything under /api/v1/admin requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
class AdminApiController {

    private final FleetService fleet;

    AdminApiController(FleetService fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/cars")
    List<CarView> cars() {
        return fleet.listCars();
    }

    @PostMapping("/cars")
    @ResponseStatus(HttpStatus.CREATED)
    CarView addCar(@Valid @RequestBody AddCarRequest request) {
        return fleet.addCar(request.plate(), request.type());
    }

    /** Soft delete; refused with 409 if existing reservations still need this car. */
    @DeleteMapping("/cars/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeCar(@PathVariable long id) {
        fleet.removeCar(id);
    }
}
