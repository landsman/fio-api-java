package cz.insuit.fio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Currency;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Fio domestic payment")
class FioDomesticPaymentTest {

    @Test
    @DisplayName("the amount is the minor units moved by the currency's decimals, exactly")
    void amount() {
        assertEquals("123.45", payment(12_345, "2000000608", null, null).amount());
        assertEquals("0.01", payment(1, "2000000608", null, null).amount());
        assertEquals("100", withCurrency("JPY").amount());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("an amount of zero or less is refused")
    void refusesANonPositiveAmount(long amount) {
        assertThrows(IllegalArgumentException.class, () -> payment(amount, "2000000608", null, null));
    }

    @Test
    @DisplayName("a currency with three decimals is refused, Fio's amount has two")
    void refusesThreeDecimals() {
        assertThrows(IllegalArgumentException.class, () -> withCurrency("BHD"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "-2000000608",
                "1234567-2000000608",
                "12345678901",
                "2000 000699",
                "2000 000699",
                "19-",
                "CZ6508000000192000145399",
                "2000000699",
                "2212-2000000608",
            })
    @DisplayName("a payee account that is not [6 digits-]10 digits, or fails modulo 11, is refused")
    void refusesAMalformedAccount(String account) {
        assertThrows(IllegalArgumentException.class, () -> payment(1, account, null, null));
    }

    @Test
    @DisplayName("a valid account passes with or without a prefix, short forms included")
    void acceptsValidAccounts() {
        payment(1, "19-2000145399", null, null);
        payment(1, "2000000608", null, null);
        payment(1, "2217-2000000608", null, null);
    }

    @Test
    @DisplayName("toString leaves out the payee's account and the texts")
    void toStringLeavesOutThePayee() {
        var text = FioImportXmlTest.FULL.toString();

        assertEquals("FioDomesticPayment[123.45 CZK to bank 0300 on 2026-12-01]", text);
    }

    @Test
    @DisplayName("a payer account failing modulo 11 is refused")
    void refusesAPayerAccountFailingTheCheck() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FioDomesticPayment(
                        "2400222222",
                        Currency.getInstance("CZK"),
                        1,
                        "2000000608",
                        "0300",
                        LocalDate.of(2026, 12, 1),
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345678901", "12a", "﻿1"})
    @DisplayName("a variable symbol that is not 1 to 10 digits is refused")
    void refusesAMalformedSymbol(String symbol) {
        assertThrows(IllegalArgumentException.class, () -> payment(1, "2000000608", symbol, null));
    }

    @Test
    @DisplayName(
            "a message over 140 characters, or with a character XML cannot carry, is refused; 140 with diacritics passes")
    void message() {
        payment(1, "2000000608", null, "ž".repeat(140));
        assertThrows(IllegalArgumentException.class, () -> payment(1, "2000000608", null, "x".repeat(141)));
        assertThrows(IllegalArgumentException.class, () -> payment(1, "2000000608", null, "line\nbreak"));
        assertThrows(IllegalArgumentException.class, () -> payment(1, "2000000608", null, "half \uD83D"));
        assertThrows(IllegalArgumentException.class, () -> payment(1, "2000000608", null, "not \uFFFE"));
        assertThrows(IllegalArgumentException.class, () -> payment(1, "2000000608", null, "not \uFFFF"));
        payment(1, "2000000608", null, "whole \uD83D\uDE00");
    }

    private static FioDomesticPayment payment(
            long amount, String accountTo, @Nullable String variableSymbol, @Nullable String message) {
        return new FioDomesticPayment(
                "2400222226",
                Currency.getInstance("CZK"),
                amount,
                accountTo,
                "0300",
                LocalDate.of(2026, 12, 1),
                variableSymbol,
                null,
                null,
                message,
                null);
    }

    private static FioDomesticPayment withCurrency(String currency) {
        return new FioDomesticPayment(
                "2400222226",
                Currency.getInstance(currency),
                100,
                "2000000608",
                "2010",
                LocalDate.of(2026, 12, 1),
                null,
                null,
                null,
                null,
                null);
    }
}
