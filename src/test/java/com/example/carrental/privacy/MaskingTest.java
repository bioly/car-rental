package com.example.carrental.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void showsOnlyTheLastFourCharacters() {
        assertThat(Masking.licence("DL12345678")).isEqualTo("****5678");
    }

}
