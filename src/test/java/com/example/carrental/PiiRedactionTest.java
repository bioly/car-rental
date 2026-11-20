package com.example.carrental;

import com.example.carrental.identity.persistence.DriverProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Objects that carry PII or secrets must not leak them through toString (and therefore through logs). */
class PiiRedactionTest {

    @Test
    void driverProfilePrintsOnlyAMaskedLicence() {
        String text = new DriverProfile(7L, "Some One", LocalDate.of(1985, 4, 12), "DL12345678").toString();

        assertThat(text).doesNotContain("DL12345678", "Some One", "1985").contains("****5678");
    }

}
