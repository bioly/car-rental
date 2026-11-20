package com.example.carrental.web.ui;

import com.example.carrental.booking.BookingException;
import com.example.carrental.booking.BookingService;
import com.example.carrental.booking.ReservationView;
import com.example.carrental.booking.ReserveCommand;
import com.example.carrental.eligibility.registry.LicenceVerificationUnavailableException;
import com.example.carrental.fleet.persistence.CarType;
import com.example.carrental.identity.security.AppPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/** Customer pages: book from the search results, list and cancel my bookings. */
@Controller
class BookingUiController {

    private final BookingService booking;

    BookingUiController(BookingService booking) {
        this.booking = booking;
    }

    @PostMapping("/bookings")
    String book(@AuthenticationPrincipal AppPrincipal me,
                @RequestParam CarType type,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                @RequestParam int days,
                RedirectAttributes redirect) {
        try {
            ReservationView booked = booking.reserve(new ReserveCommand(me.getId(), type, start, days, null));
            redirect.addFlashAttribute("success", "Booked: " + booked.type().getLabel() + " for " + booked.days()
                    + (booked.days() == 1 ? " day" : " days") + ", total " + booked.totalPrice() + " EUR. Pay at the desk.");
            return "redirect:/bookings";
        } catch (BookingException | LicenceVerificationUnavailableException e) {
            redirect.addFlashAttribute("error", messageFor(e));
            redirect.addAttribute("start", start.toString());
            redirect.addAttribute("days", days);
            return "redirect:/";
        }
    }

    @GetMapping("/bookings")
    String myBookings(@AuthenticationPrincipal AppPrincipal me, Model model) {
        model.addAttribute("reservations", booking.reservationsOf(me.getId()));
        return "bookings";
    }

    @PostMapping("/bookings/{id}/cancel")
    String cancel(@AuthenticationPrincipal AppPrincipal me, @PathVariable long id, RedirectAttributes redirect) {
        try {
            booking.cancel(me.getId(), id);
            redirect.addFlashAttribute("success", "Reservation cancelled.");
        } catch (BookingException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/bookings";
    }

    private static String messageFor(RuntimeException e) {
        if (e instanceof LicenceVerificationUnavailableException) {
            return "We could not verify your driving licence right now. Please try again in a moment.";
        }
        return e.getMessage();
    }
}
