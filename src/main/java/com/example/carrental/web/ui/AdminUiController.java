package com.example.carrental.web.ui;

import com.example.carrental.fleet.CarView;
import com.example.carrental.fleet.FleetException;
import com.example.carrental.fleet.FleetService;
import com.example.carrental.fleet.persistence.Car;
import com.example.carrental.fleet.persistence.CarType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

/** Admin dashboard (ROLE_ADMIN only, enforced in SecurityConfig): fleet. */
@Controller
@RequestMapping("/admin")
class AdminUiController {

    private final FleetService fleet;

    AdminUiController(FleetService fleet) {
        this.fleet = fleet;
    }

    @GetMapping
    String home() {
        return "redirect:/admin/fleet";
    }

    // ---- Fleet -------------------------------------------------------------------------------------

    @GetMapping("/fleet")
    String fleetPage(Model model) {
        List<CarView> cars = fleet.listCars();
        List<CapacityRow> summary = Arrays.stream(CarType.values())
                .map(t -> new CapacityRow(t, cars.stream().filter(c -> c.type() == t).count()))
                .toList();
        model.addAttribute("cars", cars);
        model.addAttribute("summary", summary);
        model.addAttribute("types", CarType.values());
        return "admin/fleet";
    }

    @PostMapping("/fleet/cars")
    String addCar(@RequestParam String plate, @RequestParam CarType type, RedirectAttributes redirect) {
        if (plate.isBlank() || plate.length() > 16) {
            redirect.addFlashAttribute("error", "Plate must be 1 to 16 characters.");
            return "redirect:/admin/fleet";
        }
        try {
            fleet.addCar(plate, type);
            redirect.addFlashAttribute("success", "Car added.");
        } catch (FleetException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/fleet";
    }

    @PostMapping("/fleet/cars/{id}/delete")
    String removeCar(@PathVariable long id, RedirectAttributes redirect) {
        try {
            fleet.removeCar(id);
            redirect.addFlashAttribute("success", "Car removed.");
        } catch (FleetException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/fleet";
    }
}
