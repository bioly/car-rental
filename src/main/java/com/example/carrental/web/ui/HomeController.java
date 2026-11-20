package com.example.carrental.web.ui;

import com.example.carrental.booking.BookingException;
import com.example.carrental.booking.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * The landing page: a search form and, once dates are entered, availability and price per car type.
 */
@Controller
class HomeController {

    /** Format expected by {@code <input type="datetime-local">}. */
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final BookingService booking;
    private final Clock clock;

    HomeController(BookingService booking, Clock clock) {
        this.booking = booking;
        this.clock = clock;
    }

    /** Search form; when pick-up and length are supplied, also shows availability per car type. */
    @GetMapping("/")
    String index(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                 @RequestParam(required = false) Integer days,
                 Model model) {
        LocalDateTime shown = start != null ? start
                : LocalDateTime.now(clock).plusDays(1).withHour(10).withMinute(0);
        model.addAttribute("startValue", shown.truncatedTo(ChronoUnit.MINUTES).format(INPUT_FORMAT));
        model.addAttribute("days", days != null ? days : 3);

        if (start != null && days != null) {
            try {
                model.addAttribute("results", booking.search(start, days, null));
            } catch (BookingException.InvalidRequest e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        return "index";
    }
}
