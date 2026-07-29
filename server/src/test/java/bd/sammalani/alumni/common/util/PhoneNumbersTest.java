package bd.sammalani.alumni.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import bd.sammalani.alumni.common.error.ApiException;

class PhoneNumbersTest {

    /**
     * All of these are one person, and the unique index can only know that if
     * exactly one form ever reaches it.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "01712345678",
            "+8801712345678",
            "8801712345678",
            "01712-345678",
            "017 1234 5678",
            "০১৭১২৩৪৫৬৭৮",       // Bangla numerals, as elders type them
    })
    @DisplayName("every way a number gets typed normalises to one form")
    void normalisesToElevenDigits(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEqualTo("01712345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "01212345678", "0171234567", "017123456789", "not a number"})
    @DisplayName("anything that cannot be a BD mobile is refused with a usable message")
    void rejectsImpossibleNumbers(String input) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(input))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("mobile number");
    }

    @Test
    @DisplayName("masking leaves enough to recognise your own number and no more")
    void masks() {
        assertThat(PhoneNumbers.mask("01712345678")).isEqualTo("017*****678");
        assertThat(PhoneNumbers.mask(null)).isNull();
    }
}
