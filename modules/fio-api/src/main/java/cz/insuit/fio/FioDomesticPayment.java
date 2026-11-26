package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * A payment order within the Czech Republic, Fio's {@code DomesticTransaction}; also a transfer in another currency
 * between two Fio accounts. Checked here against the limits of Fio's
 * <a href="https://www.fio.cz/schema/importIB.xsd">importIB.xsd</a>, so a malformed order fails in the caller's code
 * rather than as a refused batch. Account numbers are also checked against the ČNB's modulo 11 rule, so a typo in
 * one does too.
 *
 * @param accountFrom the payer's Fio account number, without a prefix or the bank code, such as {@code 2400222226}
 * @param currency the payer account's currency; Fio refuses an order whose currency is not the account's
 * @param amountInMinorUnits the amount in the currency's minor units, haléře for CZK; more than zero
 * @param accountTo the payee's account number, with an optional prefix: {@code 2000000608} or {@code
 *     2217-2000000608}
 * @param bankCode the payee's four-digit Czech bank code, such as {@code 0300}
 * @param date the day the payment is due
 * @param variableSymbol up to 10 digits, or null
 * @param constantSymbol up to 4 digits, or null
 * @param specificSymbol up to 10 digits, or null
 * @param messageForRecipient what the payee sees, up to 140 characters, or null; Fio's schema types it
 *     {@code xs:token}, so runs of spaces collapse to one and the ends are trimmed
 * @param comment the payer's own note on the order, up to 140 characters, or null; spaces collapse as above
 */
public record FioDomesticPayment(
        String accountFrom,
        Currency currency,
        long amountInMinorUnits,
        String accountTo,
        String bankCode,
        LocalDate date,
        @Nullable String variableSymbol,
        @Nullable String constantSymbol,
        @Nullable String specificSymbol,
        @Nullable String messageForRecipient,
        @Nullable String comment) {

    private static final int TEXT_LIMIT = 140;
    private static final int[] PREFIX_WEIGHTS = {10, 5, 8, 4, 2, 1};
    private static final int[] NUMBER_WEIGHTS = {6, 3, 7, 9, 10, 5, 8, 4, 2, 1};

    /** @throws IllegalArgumentException when a value breaks Fio's schema; the message names the field */
    public FioDomesticPayment {
        requireNonNull(currency, "currency");
        requireNonNull(date, "date");
        requireAccount("accountFrom", accountFrom, 10, NUMBER_WEIGHTS);
        if (amountInMinorUnits <= 0) {
            throw new IllegalArgumentException("amountInMinorUnits must be more than zero, was " + amountInMinorUnits);
        }
        // Fio's amount has at most two decimals.
        if (currency.getDefaultFractionDigits() < 0 || currency.getDefaultFractionDigits() > 2) {
            throw new IllegalArgumentException("Fio takes no amount in " + currency);
        }
        requireNonNull(accountTo, "accountTo");
        int dash = accountTo.indexOf('-');
        if (dash >= 0) {
            requireAccount("accountTo's prefix", accountTo.substring(0, dash), 6, PREFIX_WEIGHTS);
        }
        requireAccount("accountTo", accountTo.substring(dash + 1), 10, NUMBER_WEIGHTS);
        requireDigits("bankCode", bankCode, 4, 4);
        if (variableSymbol != null) {
            requireDigits("variableSymbol", variableSymbol, 1, 10);
        }
        if (constantSymbol != null) {
            requireDigits("constantSymbol", constantSymbol, 1, 4);
        }
        if (specificSymbol != null) {
            requireDigits("specificSymbol", specificSymbol, 1, 10);
        }
        requireText("messageForRecipient", messageForRecipient);
        requireText("comment", comment);
    }

    /** The amount as Fio's XML writes it, {@code 100.00}. */
    String amount() {
        return BigDecimal.valueOf(amountInMinorUnits, currency.getDefaultFractionDigits())
                .toPlainString();
    }

    /** ČNB decree 169/2011: the digits, weighted from the right, sum to a multiple of 11. */
    private static void requireAccount(String field, String value, int max, int[] weights) {
        requireDigits(field, value, 1, max);
        int sum = 0;
        for (int i = 0; i < value.length(); i++) {
            sum += (value.charAt(value.length() - 1 - i) - '0') * weights[weights.length - 1 - i];
        }
        if (sum % 11 != 0) {
            throw new IllegalArgumentException(field + " fails the modulo 11 check, was \"" + value + "\"");
        }
    }

    private static void requireDigits(String field, String value, int min, int max) {
        requireNonNull(value, field);
        if (value.length() < min || value.length() > max || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException(
                    field + " must be " + (min == max ? min : min + " to " + max) + " digits, was \"" + value + "\"");
        }
    }

    /**
     * A control character or a lone surrogate would make the XML malformed, and Fio counts characters, not UTF-16
     * units.
     */
    private static void requireText(String field, @Nullable String value) {
        if (value == null) {
            return;
        }
        if (value.codePointCount(0, value.length()) > TEXT_LIMIT) {
            throw new IllegalArgumentException(field + " is longer than " + TEXT_LIMIT + " characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " holds a control character");
        }
        if (value.codePoints().anyMatch(c -> Character.getType(c) == Character.SURROGATE)) {
            throw new IllegalArgumentException(field + " holds a lone surrogate");
        }
    }
}
