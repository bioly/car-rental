# The Pure Core: availability by peak concurrency

> **One question the whole system rests on:** *can one more rental fit in this period, given how many cars of this type we own?*
> The answer comes from one small class with **no Spring, no database, no clock**: `AvailabilityCalculator`, working on `RentalPeriod` values.

**Contents:** [1 The rule](#1-the-rule) · [2 Why it is pure](#2-why-it-is-pure) · [3 The building block](#3-the-building-block-rentalperiod) · [4 Why counting overlaps is wrong](#4-why-counting-overlaps-is-wrong) · [5 Stacks under a roof](#5-stacks-under-a-roof) · [6 The sweep line](#6-the-sweep-line) · [7 Release before acquire](#7-release-before-acquire) · [8 The code](#8-the-code) · [9 The tests are the specification](#9-the-tests-are-the-specification) · [10 Where it is used](#10-where-it-is-used) · [11 Boundaries of the core](#11-boundaries-of-the-core)

---

## 1. The rule

A request for a period **fits** when, at every moment of that period, there is still a free car.

```
   cars in use at every moment   <   cars we own
   └──────────┬───────────────┘       └────┬────┘
        highest value = peak          capacity

   so the whole check is one comparison:      peak  <  capacity
```

**What the calculator is given**

| Input | Meaning | Where it comes from |
|---|---|---|
| `candidate` | The period the customer asks for | The request |
| `existing` | Confirmed rentals of that car type that overlap the period | A database query, done by the caller |
| `capacity` | Number of active cars of that type | The fleet, passed in by the caller |

**What it returns**

| Method | Result |
|---|---|
| `peakUsage` | The highest number of existing rentals in use at the same time inside the period |
| `canAccommodate` | `true` when `peak < capacity` |
| `remaining` | `capacity − peak`, never below zero |

**One simplification:** bookings are by car **type**, not by physical car. No plate is assigned, so the comparison above is the whole decision.

---

## 2. Why it is pure

```
        ┌─────────────────────────────────────────────────────────────┐
        │                      AvailabilityCalculator                 │
 capacity ─▶                                                          │
 candidate ─▶     peakUsage · canAccommodate · remaining     ─▶  int / boolean
 existing ─▶                                                          │
        │   no fields · no Spring · no database · no time of day      │
        └─────────────────────────────────────────────────────────────┘
```

| Property | Consequence |
|---|---|
| **Same inputs → same output** (no hidden state) | Trivially testable with plain JUnit, no container |
| **Does not load data** | The caller decides *which* rentals to pass (same type, confirmed, overlapping) |
| **Does not lock or run transactions** | Concurrency is the caller's job (a per-type lock, see the booking service). The algorithm stays simple |
| **One place decides availability** | Search results, new bookings and fleet reductions all use the same code, so they cannot disagree |

---

## 3. The building block: `RentalPeriod`

```java
public record RentalPeriod(Instant start, Instant end) {

    public RentalPeriod {                                   // compact constructor = validation
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start");
    }

    public static RentalPeriod of(ZonedDateTime start, int days) {     // N calendar days, DST-safe
        return new RentalPeriod(start.toInstant(), start.plusDays(days).toInstant());
    }

    boolean overlaps(RentalPeriod other) {                  // half-open: touching is NOT overlapping
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
```

**Half-open intervals `[start, end)`:** the start belongs to the rental, the end does not.

```
   return at 12:00 ┐
                   ▼
   A  ████████████│            A = [08:00, 12:00)
   B              │████████    B = [12:00, 16:00)       A and B do NOT overlap:
 ──┼──────┼───────┼───────┼──                           a car returned at 12:00 can leave again at 12:00
  08     10      12      16
```

**Calendar days, not 24-hour blocks:** `plusDays` on a zoned date-time keeps the *local* time of day across a clock change.

```
   Europe/Warsaw, clocks go forward on 2030-03-31

   start   Sat 30 Mar 10:00 ─────────────────────────▶ end   Mon  1 Apr 10:00
                              2 calendar days = only 47 hours
```

### Why `RentalPeriod` looks like this

| Choice | The reason |
|---|---|
| **A separate type instead of two loose `Instant`s** | A rental is one idea ("this car is out from here to there"). Passing a start and an end around separately invites mistakes such as swapping them. One value keeps them together. |
| **A `record`** | The period never changes after it is created, and two periods with the same start and end are equal. That is exactly how a time span behaves, and the compiler writes the boilerplate (getters, `equals`, `hashCode`). |
| **Validation in the constructor** | `end` must be after `start`. If a broken period could be built, every method that receives one would have to check it again. Here it is checked once, so the rest of the code can simply trust it. |
| **`Instant`, not `LocalDateTime`** | An `Instant` is one exact moment, independent of time zone and daylight saving. Comparing two moments is then always unambiguous. People enter local times, and those are converted once at the edge, before a period is created. |
| **`of(ZonedDateTime, days)`** | "N days" is a calendar idea, not "N × 24 hours". Adding days to a zoned date-time keeps the same local time of day, even when the clocks change (the 47-hour example above). The one place that turns days into an end moment is here. |
| **Half-open `[start, end)`** | The end moment is not part of the rental, so a car returned at 12:00 can go out again at 12:00. Intervals that share only an end point do not overlap, and the `overlaps` method says it in two comparisons. |
| **`overlaps` has no `public`** | Only the calculator, which sits in the same package, needs it. Keeping it package-private stops other code from building its own, slightly different, definition of "overlap". |

The same half-open rule appears twice, here in `overlaps` and in the sort order of the calculator (section 7). Both have to agree, and the tests check each one on its own.

---

## 4. Why counting overlaps is wrong

Capacity **1**. Existing: A 08–12 and B 12–16. Requested: 10–14.

```
 hours        08     10     12     14     16
              ┼──────┼──────┼──────┼──────┼
 A   08-12    ██████████████
 B   12-16                  ██████████████
 you 10-14           ░░░░░░░░░░░░░░
```

```
   NAIVE   "A overlaps the request, B overlaps the request → 2 overlaps → treat as 2 cars in use"   ✘ wrong

   REAL    A and B are never out at the same time, so at every instant only ONE car is in use.
           peak = 1
             • capacity 2  → 1 < 2  fits
             • capacity 1  → 1 < 1  refused (A is out 10–12, so the single car is not free)
```

The test that pins this down (`sequentialReservationsNeverUseMoreThanOneCar`):

```java
List<RentalPeriod> existing = List.of(hours(8, 12), hours(12, 16));
assertThat(calculator.peakUsage(hours(10, 14), existing)).isEqualTo(1);       // not 2
assertThat(calculator.canAccommodate(2, hours(10, 14), existing)).isTrue();
assertThat(calculator.canAccommodate(1, hours(10, 14), existing)).isFalse();
```

What limits availability is the number of cars out **at the same time**, not the number of bookings that touch the period.

---

## 5. Stacks under a roof

Capacity **3** (the roof). Existing: A 08–12, B 11–15, C 12–16. Requested window: **10:00–14:00**.

```
 cars
 in use
    3 ┤════════════════════════════════════════  ROOF = capacity 3
    2 ┤          ██████████████████████████████  ← PEAK = 2
    1 ┤████████████████████████████████████████
    0 ┼────────────────────────────────────────
         10-11     11-12     12-13     13-14

           A       A + B     B + C     B + C      ← who is out
```

```
   peak (2)  <  capacity (3)   →  fits       free for the whole window: 3 − 2 = 1
   peak (2)  <  capacity (2)   →  refused    the stack already touches the roof
```

In short: split the requested period into moments, count the cars out at each one, and take the highest count. If that count stays below capacity, one more rental fits for the whole period.

---

## 6. The sweep line

Instead of slicing time into fixed hours, look only at the moments where something **changes**. Each overlapping rental becomes two events, **clipped to the requested window**:

```
   +1  when it starts  (or when the window starts, if it started earlier)
   −1  when it ends    (or when the window ends,   if it ends later)
```

Sort the events by time, walk through them, keep a running total and remember its maximum.

The example from section 5: capacity 3, rentals A 08-12, B 11-15 and C 12-16, requested window 10:00-14:00.

```
Rentals (solid = inside the requested window, dotted = outside it)

A 08-12 ┄┄┄┄┄┄┄┄┄┄██████████
B 11-15                ███████████████┄┄┄┄┄
C 12-16                     ██████████┄┄┄┄┄┄┄┄┄┄
                  ├───────────────────┤  requested window
        ┼────┼────┼────┼────┼────┼────┼────┼────┼
        08        10        12        14        16

Cars out at once (the running total), inside the window

    3 ┤ ═════════════════════════════════════════  capacity 3
    2 ┤                ███████████████  ← peak = 2
    1 ┤           ████████████████████
    0 ┼ ─────────────────────────────────────────
                  ▲    ▲    ▲         ▲
                 +A   +B   −A +C     −B −C   ← events
                  10   11   12        14   ← hour
```

The running total drops to 1 at 12:00 for no time at all, because A ends and C starts at the same instant (section 7 explains the order). That is why the graph shows a steady 2 from 11:00 to 14:00.

The same case as a table, event by event:

```
 time    event                    running   peak
 ─────   ──────────────────────   ───────   ────
 10:00   A starts            +1      1        1       (A began at 08:00, clipped to the window start)
 11:00   B starts            +1      2        2   ◀── tallest stack so far
 12:00   A ends              −1      1        2
 12:00   C starts            +1      2        2
 14:00   B ends              −1      1        2       (B ends 15:00, clipped to the window end)
 14:00   C ends              −1      0        2       (C ends 16:00, clipped to the window end)
                                              ═══
                                    peakUsage = 2
```

### Why those steps?

Think of the barrier at a car park. Every car that drives in adds one to the counter, every car that drives out takes one off. To know the busiest the car park ever got, you do not need to know which car is which. You only need the counter, and you have to read the events **in the order they happened**.

- **Sort by time.** The counter only makes sense if the events are replayed in the order they really happened. Our list is built rental by rental ("A starts, A ends, B starts, ..."), which is not time order. Sorting puts everything on one timeline.
- **Walk through them.** Once the events are in order, one pass is enough. Each event changes the number of cars out by exactly one, so there is no need to compare every rental with every other rental.
- **Keep a running total.** The total after an event is the number of cars out at that moment. It goes up at a start, and down at an end.
- **Remember the maximum.** We are not interested in the number at the end (it is always back to zero), but in the *highest* point reached, because that is the moment when we are closest to running out of cars. If even that moment leaves a free car, every other moment does too.

A good way to see why this is enough: the total only changes at events, so between two events it stays the same. Checking the total right after each event therefore covers every moment of the period, even though we never look at the time in between.

An alternative would be to check every minute of the period against every rental. That also works, but it is slow and arbitrary (why minutes?). The sweep looks only at the moments where something actually changes, and the cost is dominated by the sort.


Rentals that do not overlap the window at all are skipped before any events are created (`overlapOutsideWindowIgnored`).

---

## 7. Release before acquire

Events at the **same instant** are ordered so that a **−1 comes before a +1**: `thenComparingInt(Event::delta)` sorts −1 ahead of +1.

```
 at 12:00:   A ends (−1)   and   C starts (+1)

   WRONG order  (+1 first):   2 ──(+C)──▶ 3 ──(−A)──▶ 2        peak = 3   ✘ a phantom third car
   RIGHT order  (−1 first):   2 ──(−A)──▶ 1 ──(+C)──▶ 2        peak = 2   ✔
```

This one line is what makes **back-to-back rentals** work, and it is the same half-open rule as `RentalPeriod.overlaps`. Test: `backToBackIsAllowed`.

---

## 8. The code

```java
public final class AvailabilityCalculator {

    private record Event(Instant at, int delta) { }

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
}
```

| Detail | Why |
|---|---|
| `final` class, no fields | Nothing to mutate, safe to share between threads |
| `private record Event` | A tiny value type, invisible outside the class |
| Clip with `max(start)` / `min(end)` | Only the part **inside the window** counts |
| `Math.max(0, ...)` in `remaining` | Never reports a negative number of cars |
| Complexity **O(n log n)** | n = rentals overlapping the window (the sort dominates) |

---

## 9. The tests are the specification

All plain JUnit, no Spring (`AvailabilityCalculatorTest`, `RentalPeriodTest`).

| Test | Scenario | What it proves |
|---|---|---|
| `emptyFleetUsage` | no rentals | peak 0, capacity ≥ 1 fits |
| `zeroCapacity` | capacity 0 | never accommodates, remaining 0 |
| `partialOverlapBlocksSingleCar` | 08–11 vs request 10–14 | capacity 1 refuses, capacity 2 accepts |
| `backToBackIsAllowed` | 10–12 then 12–14, and 8–10 before 10–12 | touching ends do not conflict |
| `sequentialReservationsNeverUseMoreThanOneCar` | 08–12, 12–16 vs 10–14 | **peak 1, not a count of 2** |
| `concurrentReservationsExhaustCapacity` | 08–14, 10–16 vs 11–12 | peak 2: capacity 2 refuses, 3 accepts |
| `overlapOutsideWindowIgnored` | overlap only before 12:00, request 12–15 | clipping to the window works |
| `remainingCars` | 08–14, 10–16 vs 09–11 | free cars = capacity − peak |
| `endMustBeAfterStart` | zero or negative length | invalid periods cannot be built |
| `overlapIsHalfOpen` | touching vs overlapping | the `overlaps` rule, both directions |
| `daysAreCalendarDaysAndKeepLocalTimeAcrossDst` | 2 days across a Warsaw clock change | 47 hours, same local time of day |

One of them as a picture (`concurrentReservationsExhaustCapacity`):

```
 existing  08-14 and 10-16,   request 11-12

 cars
 in use
    2 ┤██████████████████████████████   ← both cars out for the whole request: peak = 2
    1 ┤██████████████████████████████
    0 ┼──────────────────────────────
         10    11    12    13    14
                 ░░░░░  request

   capacity 2:  2 < 2  → refused          capacity 3:  2 < 3  → accepted
```

---

## 10. Where it is used

```
   search availability   (no lock, a hint)        ──┐
   make a reservation    (under a per-type lock)  ──┼──▶  AvailabilityCalculator
   remove a car          (capacity guard)         ──┘      peakUsage · canAccommodate · remaining
```

- **Reservation:** the booking service takes a lock on the car type, loads the confirmed rentals that overlap the window, and calls `canAccommodate(capacity, period, existing)`.
- **Search:** `remaining(...)` for each type (a hint, not a promise: only the locked check at booking time is binding).
- **Removing a car:** `ReservationCapacityGuard` checks that future commitments still fit into the reduced capacity, `peakUsage(...) <= newCapacity`, using this same class.

The pure core says **whether** it fits. The **services around it** make that answer safe under concurrency (locking) and supply the data.

---

## 11. Boundaries of the core

| Not in the core | Where it lives / what it would take |
|---|---|
| Concurrency control | The booking service's per-type database lock |
| Which rentals are passed in | The repository query: same type, confirmed, overlapping the window |
| Physical car assignment | Deliberately absent (bookings are by type) |
| Turnaround time (cleaning, inspection) | Extend each existing period by a gap inside this class: the only place that decides availability |
| Overbooking policy | Compare against a configured limit instead of `capacity` in one method |
| Scale | O(n log n) per request on the overlapping rentals only; an index on type, status and start time helps the query |

---

## Appendix

### A. Commands for the explanation

Requirements: JDK 21 and Maven 3.9+.

**The core, tests only (plain JUnit, no Spring, runs in seconds)**

```bash
mvn -Dtest=AvailabilityCalculatorTest test        # the calculator: peak, back-to-back, clipping
mvn -Dtest=RentalPeriodTest test                  # half-open intervals, DST
mvn -Dtest='AvailabilityCalculatorTest,RentalPeriodTest' test      # both
```

**The requirements and the concurrency proof (start the application context, use the in-memory database)**

```bash
mvn -Dtest=CarRentalRequirementsTest test         # one test per requirement of the assessment
mvn -Dtest=BookingConcurrencyIntegrationTest test # many threads race for the last cars
```

**Everything**

```bash
mvn test                                          # all tests
mvn clean verify                                  # build, all tests, create target/car-rental.jar
```

**Run the application**

```bash
mvn clean verify
java -jar target/car-rental.jar                   # http://localhost:8080
```

A demo fleet is loaded at startup: **3 Sedans, 2 SUVs, 1 Van**. `APP_DEMO_DATA=false` starts it empty.

| Account | Password | Use |
|---|---|---|
| `alice@example.com` | `Password123!` | A normal customer, valid licence |
| `young@example.com` | `Password123!` | Aged 22: Sedan and SUV yes, Van no |
| `admin@carrental.local` | `ChangeMe-123` | Admin (add and remove cars) |

**A short live demo of the limited stock** (two SUVs exist, so the third booking for the same period should be refused)

```bash
# 1. What is free for the period? (public)
curl -s 'localhost:8080/api/v1/availability?start=2030-06-10T10:00&days=3'

# 2. Two customers each reserve an SUV for the same period
curl -s -u alice@example.com:Password123! -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"type":"SUV","start":"2030-06-10T10:00","days":3}'

curl -s -u young@example.com:Password123! -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{"type":"SUV","start":"2030-06-10T10:00","days":3}'

# 3. A third request for the same period: expected HTTP 409, no SUV left
curl -s -i -u alice@example.com:Password123! -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{"type":"SUV","start":"2030-06-10T10:00","days":3}'

# 4. Back-to-back: an SUV starting at 10:00 on the 13th, the moment the first two return, should be accepted
curl -s -u alice@example.com:Password123! -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{"type":"SUV","start":"2030-06-13T10:00","days":1}'

# 5. Retry safely: repeat the first request of step 2 (same Idempotency-Key) and the original reservation comes back
curl -s -u alice@example.com:Password123! -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"type":"SUV","start":"2030-06-10T10:00","days":3}'
```

The expected results follow from the demo fleet and the rules in this document. Try the sequence once before showing it. The database is in memory, so restart the application to get a clean state.

### B. Database structure

Four tables. Hibernate creates them on startup in the in-memory H2 database.

```
┌──────────────────────────────────┐        ┌────────────────────────────────────────┐
│ app_user                         │        │ reservation                            │
├──────────────────────────────────┤        ├────────────────────────────────────────┤
│ id            PK                 │        │ id               PK                    │
│ email         UNIQUE             │        │ user_id                                │
│ password_hash  (BCrypt)          │        │ car_type          SEDAN|SUV|VAN        │
│ full_name                        │        │ starts_at         (UTC)                │
│ date_of_birth                    │        │ ends_at           (UTC)                │
│ licence_number (PII)             │        │ days                                   │
│ role          USER | ADMIN       │        │ total_price       (10,2)               │
│ enabled                          │        │ status            CONFIRMED|CANCELLED  │
└──────────────────────────────────┘        │ created_at                             │
                                            │ idempotency_key   (optional)           │
                                            │                                        │
                                            │ UNIQUE (user_id, idempotency_key)      │
                                            └────────────────────────────────────────┘

┌──────────────────────────────┐        ┌──────────────────────────┐
│ car                          │        │ car_category             │
├──────────────────────────────┤        ├──────────────────────────┤
│ id        PK                 │        │ code   PK                │
│ plate                        │        │         SEDAN            │
│ car_type  SEDAN|SUV|VAN      │        │         SUV              │
│ active    (soft delete)      │        │         VAN              │
└──────────────────────────────┘        │                          │
                                        │ lock handle only:        │
                                        │ SELECT ... FOR UPDATE    │
                                        └──────────────────────────┘
```

**Links between the tables are by value only (there are no foreign keys, on purpose):**

```
 reservation.user_id    ┄┄▶  app_user.id          who booked
 reservation.car_type   ┄┄▶  car_category.code    which type was booked
 car.car_type           ┄┄▶  car_category.code    which type the car is
```

| Table | Role |
|---|---|
| `reservation` | One row per booking. Not tied to a physical car: it counts against the capacity of its type. Cancelling changes `status`, the row stays |
| `car` | The physical fleet. **Capacity of a type = number of rows with `active = true` for that type.** Removing a car sets `active = false` |
| `car_category` | Three rows, one per type. Used only as a **lock handle** (`SELECT ... FOR UPDATE`), so bookings and fleet changes for one type are serialised |
| `app_user` | Customers and the administrator. The password is stored as a BCrypt hash, the licence number is personal data and is masked when shown |

**Constraints that matter:**
- `UNIQUE (user_id, idempotency_key)` on `reservation`: a retried request cannot create a second booking.
- `UNIQUE (email)` on `app_user`.
- Times are stored as UTC moments. The price is stored at booking time, so a later price change does not rewrite history.
