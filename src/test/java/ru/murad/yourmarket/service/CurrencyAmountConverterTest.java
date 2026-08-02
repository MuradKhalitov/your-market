package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class CurrencyAmountConverterTest {
    private final CurrencyAmountConverter converter = new CurrencyAmountConverter();

    @ParameterizedTest
    @CsvSource({"1.00,100", "50.00,5000", "99.00,9900", "199.99,19999"})
    void convertsRubToKopecks(String amount, int expected) {
        assertEquals(expected, converter.toMinorUnits(new BigDecimal(amount), "RUB"));
    }

    @Test void rejectsMoreThanTwoFractionDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.toMinorUnits(new BigDecimal("1.001"), "RUB"));
    }

    @Test void rejectsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.toMinorUnits(BigDecimal.ZERO, "RUB"));
    }

    @Test void rejectsSdkIntegerOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.toMinorUnits(new BigDecimal("21474836.48"), "RUB"));
    }

    @ParameterizedTest
    @CsvSource({"1,1", "5,5", "100,100"})
    void convertsStarsWithoutMultiplyingByOneHundred(String amount, int expected) {
        assertEquals(expected, converter.toMinorUnits(new BigDecimal(amount), "XTR"));
    }

    @Test void rejectsFractionalStars() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.toMinorUnits(new BigDecimal("1.5"), "XTR"));
    }

    @Test void rejectsZeroStars() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.toMinorUnits(BigDecimal.ZERO, "XTR"));
    }
}
