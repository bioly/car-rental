package com.example.carrental.booking.availability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalPeriodTest {

    private static final Instant T0 = Instant.parse("2030-06-10T10:00:00Z");

    @Test
    void endMustBeAfterStart() {
        assertThatThrownBy(() -> new RentalPeriod(T0, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RentalPeriod(T0, T0.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlapIsHalfOpen() {
        RentalPeriod a = new RentalPeriod(T0, T0.plus(Duration.ofHours(2)));
        RentalPeriod touching = new RentalPeriod(T0.plus(Duration.ofHours(2)), T0.plus(Duration.ofHours(4)));
        RentalPeriod overlapping = new RentalPeriod(T0.plus(Duration.ofHours(1)), T0.plus(Duration.ofHours(3)));

        assertThat(a.overlaps(touching)).isFalse();
        assertThat(touching.overlaps(a)).isFalse();
        assertThat(a.overlaps(overlapping)).isTrue();
        assertThat(overlapping.overlaps(a)).isTrue();
    }

    @Test
    void daysAreCalendarDaysAndKeepLocalTimeAcrossDst() {
        // Europe/Warsaw switches to summer time on 2030-03-31, so two calendar days are only 47 hours.
        ZoneId warsaw = ZoneId.of("Europe/Warsaw");
        RentalPeriod period = RentalPeriod.of(LocalDateTime.of(2030, 3, 30, 10, 0).atZone(warsaw), 2);

        assertThat(Duration.between(period.start(), period.end())).isEqualTo(Duration.ofHours(47));
        assertThat(period.end().atZone(warsaw).toLocalDateTime()).isEqualTo(LocalDateTime.of(2030, 4, 1, 10, 0));
    }
}
