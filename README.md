# Car Rental

A small car rental system. Customers pick a car type (**Sedan, SUV or Van**), a pick-up date and time and a number of
days. A reservation is accepted only if a car of that type is free for the **whole** period. Stock per type is limited.

It is one Spring Boot jar with a REST API and a simple dark-mode web UI, using an in-memory H2 database.

**Where to look first:** the core of the project is `booking.availability.AvailabilityCalculator` and
`booking.BookingService`. The proof that the requirements hold is `CarRentalRequirementsTest`. Everything else exists
to make that core usable and safe.

## Run it

You need **JDK 21** and **Maven 3.9+**. Nothing else.

```bash
mvn clean verify                   # build, run the tests, create the jar
java -jar target/car-rental.jar    # http://localhost:8080
```

A demo fleet (3 Sedans, 2 SUVs, 1 Van) and a few demo customers are loaded at startup, so you can log in and book
right away. Set `APP_DEMO_DATA=false` to start empty.

| Account | Password | What it shows |
|---|---|---|
| `admin@carrental.local` | `ChangeMe-123` | Admin dashboard (add / remove cars). Change it with `APP_ADMIN_PASSWORD` |
| `alice@example.com` | `Password123!` | The happy path |
| `young@example.com` | `Password123!` | Aged 22: can rent a Sedan or SUV, not a Van (25+) |
| `invalid@example.com` | `Password123!` | Licence not valid: booking rejected |
| `shortlicence@example.com` | `Password123!` | Licence expires in 10 days: longer rentals rejected |
| `flaky@example.com` | `Password123!` | Licence registry fails: retries, then 503 "try again" |
| `slow@example.com` | `Password123!` | Licence registry times out: cut off after 2 s |

The licence check is a fake government registry. It answers by the licence number prefix: `INV` is invalid, `SHORT` is
valid for 10 days, `FAIL` is a technical failure, `SLOW` is a 5 second response, anything else is valid for 5 years.
Register your own customer with such a prefix to try each case.

## REST API

Everything is under `/api/v1`, and errors are RFC 7807 `application/problem+json`.

```bash
# Register (public). The response only contains a masked licence number.
curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' -d '{
  "email":"me@example.com","password":"Password123!","fullName":"Me Myself",
  "dateOfBirth":"1988-05-14","licenceNumber":"DL12345678"}'

# What is free, and for how much (public)
curl -s 'localhost:8080/api/v1/availability?start=2030-06-10T10:00&days=3'

# Reserve (HTTP Basic). The Idempotency-Key makes a retry safe.
curl -s -u me@example.com:Password123! -X POST localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: my-booking-1' \
  -d '{"type":"SEDAN","start":"2030-06-10T10:00","days":3}'

# My reservations, and cancel one
curl -s -u me@example.com:Password123! localhost:8080/api/v1/reservations
curl -s -u me@example.com:Password123! -X DELETE localhost:8080/api/v1/reservations/1

# Admin: add a car (the demo loader does this for you)
curl -s -u admin@carrental.local:ChangeMe-123 -X POST localhost:8080/api/v1/admin/cars \
  -H 'Content-Type: application/json' -d '{"plate":"VAN-999","type":"VAN"}'
```

| Endpoint | Who | Notes |
|---|---|---|
| `POST /users` | anyone | 201, 400, 409 (email taken) |
| `GET /availability?start=&days=` | anyone | Free cars and price per type |
| `POST /reservations` | customer | 201, 409 no car free, 422 not eligible (with a reason code), 503 licence check unavailable (`Retry-After`) |
| `GET /reservations`, `DELETE /reservations/{id}` | customer | 403 if not yours, 409 after pick-up |
| `GET/POST /admin/cars`, `DELETE /admin/cars/{id}` | admin | 409 if existing reservations still need the car |

## How it works

**Availability is checked at every moment of the rental, not only at pick-up.** A request fits if, at no instant, the
number of overlapping reservations reaches the capacity. This is a sweep line over the interval boundaries. Simply
counting overlaps with the whole period would wrongly refuse a request that touches two reservations which never
overlap each other. Intervals are half-open, so a return at 10:00 and a pick-up at 10:00 do not clash. "Days" are
calendar days in the rental timezone, so daylight saving does not shift the return time.

**No car is assigned at booking time.** A reservation reserves a *type* against capacity. Assigning a plate early can
refuse bookings that would fit after reshuffling. Choosing the physical car is a pick-up step, which is out of scope.

**Concurrency.** A booking first locks one row for the car type (`SELECT ... FOR UPDATE`), then checks availability and
saves. Two people racing for the last car are serialised, and different types never block each other. The slow call to
the licence registry happens **before** that transaction, so no database lock is held while waiting for the network.

**Eligibility is a list of small rule objects**, run in order, and the first rejection wins: minimum age 21, vans 25,
licence valid, licence valid through the return day. The cheap local rules run first, so the registry is called only
when it is needed. A new rule is a new class.

**Retries are safe.** An `Idempotency-Key` plus a unique database constraint means a repeated request, even two at the
same moment, creates one reservation.

**Removing a car respects existing bookings.** It is refused if, at some future moment, the remaining cars could no
longer cover the reservations. It reuses the same calculator.

### Resilience for the registry call

The registry sits behind the `LicenceVerifier` interface. `ResilientLicenceVerifier` wraps it with the standard
resiliency patterns, written in plain Java:

- a small thread pool and queue (**bulkhead**), so a slow registry cannot take the whole app with it;
- a **timeout** (2 s) that cancels the call;
- up to 2 attempts with exponential backoff and jitter, **only** for technical failures. "Licence not valid" is a normal
  answer and is never retried;
- a **circuit breaker** that opens after 5 failures in a row, waits 30 s, then lets one trial call through;
- **fail closed**: if the registry cannot be asked, there is no reservation, and the caller gets a 503 with `Retry-After`.

To plug in the real registry, implement `LicenceVerifier`, register it as a bean named `governmentLicenceClient` and set
`app.licence.provider` to anything but `fake`. Nothing else changes.

### Structure

The code is split **by feature**, with the web layer on top. Dependencies point one way:
`web -> booking -> eligibility -> fleet`, plus `booking -> identity`. `PackageDependencyRulesTest` (ArchUnit) fails
the build on a cycle or a wrong-way dependency, and also makes sure only a feature itself uses its repositories.

There are only three interfaces, and each has a reason: `LicenceVerifier` (fake, real, resilient wrapper),
`EligibilityRule` (one class per rule) and `CapacityGuard`. `fleet` defines `CapacityGuard` and `booking` implements it,
so `booking` can veto removing a car without `fleet` depending on `booking`. Everything else is a plain class.

Each feature that owns tables keeps its entities and repositories in a `persistence` sub-package, so what lands in the
database is easy to find:

| Table | Entity | Feature |
|---|---|---|
| `app_user` | `identity.persistence.AppUser` | identity |
| `car_category` | `fleet.persistence.CarCategory` (capacity per type, and the lock row) | fleet |
| `car` | `fleet.persistence.Car` | fleet |
| `reservation` | `booking.persistence.Reservation` | booking |

```
src/main/java/com/example/carrental
  booking/        BookingService, ReservationCapacityGuard
    availability/ AvailabilityCalculator, RentalPeriod
    persistence/  Reservation, ReservationRepository
  fleet/          FleetService, CapacityGuard
    persistence/  Car, CarCategory, CarType
  identity/       registration, directory service
    security/     login support
    persistence/  AppUser, Role
  eligibility/    RentalEligibility, EligibilityRule
    rules/        age, van age, licence valid, licence valid through return
    registry/     LicenceVerifier, fake registry, ResilientLicenceVerifier, CircuitBreaker
  web/            api/ (REST)   ui/ (Thymeleaf)
  config/         security, clock
  privacy/        Masking
```

## Tests

```bash
mvn test
mvn -Dtest=AvailabilityCalculatorTest test      # a single class
```

| Area | Tests | What they prove |
|---|---|---|
| Requirements | `CarRentalRequirementsTest` | **Start here.** One test per requirement: a type, a date, N days; three types; limited stock; separate stock per type; cancelling frees a car; back-to-back rentals work; overlaps are refused |
| Availability | `AvailabilityCalculatorTest`, `RentalPeriodTest` | Plain JUnit: peak concurrency versus a naive overlap count, back-to-back, daylight saving |
| Booking | `BookingServiceTest` | Eligibility before inventory, fail closed, idempotent replay, cancel rules |
| Eligibility | `EligibilityRulesTest`, `RentalEligibilityTest` | Each rule on its own, and the registry is **not** called when a cheap rule already rejects |
| Resilience | `ResilientLicenceVerifierTest`, `CircuitBreakerTest` | Retry only on technical errors, the circuit opens and then fails fast, timeout |
| Concurrency | `BookingConcurrencyIntegrationTest` | Many threads race for the last cars, and exactly `capacity` succeed |
| Database | `ReservationRepositoryIntegrationTest` | Query behaviour on a real database, unique idempotency key |
| Web and data | `ReservationApiIntegrationTest`, `AdminApiIntegrationTest`, `UiSmokeIntegrationTest`, PII tests | A few end-to-end paths: login, a full booking, 422 and 503, no personal data in logs |
| Structure | `PackageDependencyRulesTest` | No cycles, one-way dependencies |

The goal of the tests is to prove the requirements, not to maximise coverage. So I put the tests where the logic is hard
(availability, concurrency, eligibility, resilience) and kept the web, UI and PII layers to a few smoke tests. Coverage
is not measured, and most validation messages and error codes are not checked one by one.

## Personal data

- No card or payment data is collected, stored or logged. Payment happens at the desk, so PCI scope is nil.
- Passwords are stored as BCrypt hashes.
- The licence number is shown only masked (`****5678`), in the API, the UI and in `toString()`.
- Logs contain ids and outcomes only. Error responses never echo what the user sent.
- The registry only says valid or not valid, plus an expiry date, never why.

## Settings

| Setting | Default | Meaning |
|---|---|---|
| `APP_DEMO_DATA` | `true` | Load the demo fleet and customers at startup |
| `APP_ADMIN_PASSWORD` | `ChangeMe-123` | Initial admin password |
| `app.rental.zone`, `app.rental.max-days` | `UTC`, `30` | Rental timezone, longest rental |
| `app.eligibility.min-age`, `van-min-age` | `21`, `25` | Age rules |
| `app.licence.resilience.*` | see `application.yml` | Timeout, retries, breaker, bulkhead |
| `app.licence.fake.latency` | `0ms` | Slow down the fake registry |

## Assumptions

- One rental location and one timezone. Pick-up and return times are local to it.
- Rentals last 1 to 30 days, pick-up must be in the future, and cancelling is free until pick-up.
- A flat daily price per type (Sedan 40, SUV 65, Van 85, EUR). No taxes, extras or insurance.
- A licence number is 8 to 12 letters or digits.
- About 100 daily users: one instance, no cache, no message broker.

## Left out on purpose, and what I would do next

I favoured depth in the booking core over breadth, and kept the rest thin. These are choices, not oversights.

**Limits of what is here**
- **H2 in memory.** Data is gone after a restart and the schema comes from Hibernate. The code is plain JPA with no
  vendor SQL, so moving to PostgreSQL means a driver, settings and migrations (Flyway), plus a lock timeout. The
  per-type lock is only tested on H2.
- **Plate uniqueness** is checked under the per-type lock, not by a database constraint.
- **Login is basic.** HTTP Basic for the API (needs HTTPS in production) and a form login for the UI. No email
  verification, password reset, lockout or MFA. No rate limiting either.
- **Default secrets.** The admin password has a development default, to be overridden in any real setup.

**Things I chose not to build**
- **Hand-written resilience instead of Resilience4j.** I wanted every line to be explainable and testable in a short
  review, and one dependency fewer. The cost: the breaker counts *consecutive* failures, not a failure rate over a
  window, and there are no metrics. For production I would use Resilience4j with Micrometer; the `LicenceVerifier`
  interface means the swap touches one class.
- **User administration and erasure** (listing users, anonymising a customer). The first thing to settle is what happens
  to the customer's future reservations: cancel them and free the capacity, or keep them for accounting, and for how
  long. The `enabled` flag on `AppUser` is already honoured at login and would be the switch for locking an account.
- **Encrypting the licence number at rest.** It is stored as plain text, and the database is the trust boundary. Next:
  encrypt it with a key from a KMS using a JPA `AttributeConverter`, or tokenise it. Decide first whether it must be
  searchable, because encryption rules that out, and plan key rotation.
- **Turnaround time between rentals** (cleaning, inspection). `AvailabilityCalculator` is the only place that decides
  availability, so extending each reservation by a configurable gap would go there.
- **Physical car assignment and a pick-up / return workflow**, which is the natural next step after booking by type.
- **Online payment.** It would bring PCI scope, so it should go through a hosted payment page and never touch card data.
- **Operations:** a health endpoint, a CI pipeline (`mvn verify`), metrics for the resilience layer, and token-based
  authentication.
