package com.example.carrental.booking.availability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Pure domain logic (no Spring, no persistence): decides whether a car type with a given
 * capacity can accommodate one more rental.
 *
 * <p>A request is feasible iff, at <em>every instant</em> of the requested period, the number
 * of overlapping reservations is lower than the capacity. Counting overlaps against the
 * requested period as a whole would be wrong: two reservations that overlap the request but
 * not each other only ever use one car at a time.
 *
 * <p>Implemented as a sweep line over interval boundaries clipped to the requested window:
 * O(n log n) in the number of overlapping reservations.
 */
public final class AvailabilityCalculator {

    private record Event(Instant at, int delta) {
    }

    /** Highest number of existing reservations in use simultaneously within the candidate period. */
    public int peakUsage(RentalPeriod candidate, Collection<RentalPeriod> existing) {
        RentalPeriod window = candidate;
        List<Event> events = new ArrayList<>();
        for (RentalPeriod reservation : existing) {
            RentalPeriod occupied = reservation;
            if (!occupied.overlaps(window)) {
                continue;
            }
            events.add(new Event(max(occupied.start(), window.start()), +1));
            events.add(new Event(min(occupied.end(), window.end()), -1));
        }
        // At the same instant, releases (-1) are processed before acquisitions (+1): half-open intervals.
        events.sort(Comparator.comparing(Event::at).thenComparingInt(Event::delta));

        int running = 0;
        int peak = 0;
        for (Event event : events) {
            running += event.delta();
            peak = Math.max(peak, running);
        }
        return peak;
    }

    /** Number of cars of this type free for the whole candidate period. */
    public int remaining(int capacity, RentalPeriod candidate, Collection<RentalPeriod> existing) {
        return Math.max(0, capacity - peakUsage(candidate, existing));
    }

    public boolean canAccommodate(int capacity, RentalPeriod candidate, Collection<RentalPeriod> existing) {
        return peakUsage(candidate, existing) < capacity;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
