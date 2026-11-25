package cz.insuit.fio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Fio movement")
class FioTransactionTest {

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"-15.00, CZK, -1500", "0.01, CZK, 1", "1250.5, EUR, 125050", "1500, JPY, 1500", "7, XXX, 7"})
    @DisplayName("the amount in minor units keeps its sign and its value")
    void minorUnits(String amount, String currency, long minor) {
        assertEquals(minor, movement(amount, currency).amountInMinorUnits());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.005", "92233720368547758.08"})
    @DisplayName("an amount that does not fit exactly is refused, never rounded")
    void notExact(String amount) {
        assertThrows(ArithmeticException.class, () -> movement(amount, "CZK").amountInMinorUnits());
    }

    @Test
    @DisplayName("a currency the JDK does not know is refused")
    void unknownCurrency() {
        assertThrows(
                IllegalArgumentException.class, () -> movement("1.00", "QQQ").amountInMinorUnits());
    }

    private static FioTransaction movement(String amount, String currency) {
        return new FioTransaction(
                1L,
                LocalDate.of(2026, 1, 1),
                Instant.parse("2025-12-31T23:00:00Z"),
                new BigDecimal(amount),
                currency,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "{}");
    }
}
