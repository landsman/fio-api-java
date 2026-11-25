package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One movement on a Fio account, named in the documentation's words rather than by the {@code columnNN} keys the
 * JSON uses.
 *
 * <p>{@link #id} is Fio's <i>ID pohybu</i>: unique on the account and never reused, so it is the key to store a
 * movement exactly once. {@link #instructionId}, <i>ID pokynu</i>, is not: one foreign payment books a movement for
 * the transfer and another for its fee, under one instruction.
 *
 * @param id <i>ID pohybu</i>, unique per movement and never reused
 * @param date the booking day, as Fio wrote it
 * @param bookedAt the instant Fio sent: midnight of {@link #date}, in the offset Fio gave or in {@code Europe/Prague}
 * @param amount signed, negative is money leaving; exactly as Fio wrote it, scale included
 * @param currency ISO 4217, such as {@code CZK}
 * @param counterpartyAccount the other party's account number; null for interest, fees and the like
 * @param constantSymbol <i>konstantní symbol</i>
 * @param variableSymbol <i>variabilní symbol</i>, what a payer is asked to quote; leading zeros kept
 * @param specificSymbol <i>specifický symbol</i>
 * @param userIdentification <i>uživatelská identifikace</i>
 * @param messageForRecipient <i>zpráva pro příjemce</i>, free text the payer wrote
 * @param type <i>typ pohybu</i>, such as {@code Platba převodem uvnitř banky}; free text
 * @param executedBy <i>provedl</i>
 * @param specification <i>upřesnění</i>, usually the original amount and currency of a converted payment
 * @param instructionId <i>ID pokynu</i>; not unique, see above
 * @param payerReference <i>reference plátce</i>
 * @param rawJson this movement's JSON as Fio sent it, so a caller can store the original rather than what this
 *     version understood of it
 */
public record FioTransaction(
        long id,
        LocalDate date,
        Instant bookedAt,
        BigDecimal amount,
        String currency,
        @Nullable String counterpartyAccount,
        @Nullable String counterpartyName,
        @Nullable String counterpartyBankCode,
        @Nullable String counterpartyBankName,
        @Nullable String counterpartyBic,
        @Nullable String constantSymbol,
        @Nullable String variableSymbol,
        @Nullable String specificSymbol,
        @Nullable String userIdentification,
        @Nullable String messageForRecipient,
        @Nullable String type,
        @Nullable String executedBy,
        @Nullable String specification,
        @Nullable String comment,
        @Nullable Long instructionId,
        @Nullable String payerReference,
        String rawJson) {

    public FioTransaction {
        requireNonNull(date, "date");
        requireNonNull(bookedAt, "bookedAt");
        requireNonNull(amount, "amount");
        requireNonNull(currency, "currency");
        requireNonNull(rawJson, "rawJson");
    }

    /** Only what identifies the movement: the rest names people and their accounts, which a log should not hold. */
    @Override
    public String toString() {
        return "FioTransaction[id=" + id + ", date=" + date + ", amount=" + amount + " " + currency + "]";
    }
}
