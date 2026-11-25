package cz.insuit.fio;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns Fio's {@code transactions.json} into a {@link FioAccountStatement}; the only place that knows what {@code
 * column5} means. Every field arrives as {@code "column5": {"value": …, "name": "VS", "id": 5}} or as {@code
 * "column5": null}, and the numbers are not contiguous.
 *
 * <p>A date comes in two shapes, and both are real: epoch milliseconds ({@code 1340661600000}, the documentation's
 * JSON examples) or a date with an offset ({@code 2015-03-30+0200}, its field table and responses captured in the
 * wild; {@code 2012-07-27+02:00} in its XML). All are read.
 */
final class FioStatementParser {

    /** The zone Fio's epoch-millisecond dates are midnight in. */
    private static final ZoneId BANK_ZONE = ZoneId.of("Europe/Prague");

    private static final JsonMapper JSON = JsonMapper.builder()
            // A number read into a JsonNode is a double by default; an amount is money, so it must stay exact.
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            // Without this, -15.00 arrives as -15: the value survives, the scale Fio sent does not.
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    /** {@code 2015-03-30}, then an offset written {@code +02:00}, {@code +0200}, {@code Z}, or none. */
    private static final DateTimeFormatter OFFSET_DATE = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .optionalStart()
            .appendOffset("+HH:MM", "Z")
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "Z")
            .optionalEnd()
            .toFormatter();

    private FioStatementParser() {}

    /**
     * @throws FioException.InvalidResponse when the body is not a statement: a half-read statement is worse than
     *     none, since an empty one reads as a quiet account
     */
    static FioAccountStatement parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException e) {
            throw new FioException.InvalidResponse("Fio answered with a body that is not JSON", e);
        }
        var statement = present(root.path("accountStatement"));
        if (statement == null) {
            throw new FioException.InvalidResponse("Fio's answer has no accountStatement", null);
        }
        var info = present(statement.path("info"));
        if (info == null) {
            throw new FioException.InvalidResponse("Fio's statement has no info header", null);
        }
        var list = statement.path("transactionList").path("transaction");
        var transactions = new ArrayList<FioTransaction>();
        if (list.isArray()) {
            for (var node : list) {
                transactions.add(transaction(node));
            }
        }
        return new FioAccountStatement(info(info), List.copyOf(transactions));
    }

    private static FioAccountInfo info(JsonNode info) {
        return new FioAccountInfo(
                text(info.path("accountId")),
                text(info.path("bankId")),
                text(info.path("currency")),
                text(info.path("iban")),
                text(info.path("bic")),
                decimal(info.path("openingBalance")),
                decimal(info.path("closingBalance")),
                number(info.path("idFrom")),
                number(info.path("idTo")),
                number(info.path("idLastDownload")));
    }

    private static FioTransaction transaction(JsonNode node) {
        var id = number(column(node, 22));
        if (id == null) {
            throw new FioException.InvalidResponse("A movement has no ID pohybu; refusing to return it unkeyed", null);
        }
        var dateNode = column(node, 0);
        if (dateNode == null) {
            throw new FioException.InvalidResponse("Movement " + id + " has no date", null);
        }
        var amount = decimal(column(node, 1));
        if (amount == null) {
            throw new FioException.InvalidResponse("Movement " + id + " has no amount", null);
        }
        var currency = text(column(node, 14));
        if (currency == null) {
            throw new FioException.InvalidResponse("Movement " + id + " has no currency", null);
        }
        var booked = booked(id, dateNode);
        return new FioTransaction(
                id,
                booked.date(),
                booked.instant(),
                amount,
                currency,
                text(column(node, 2)),
                text(column(node, 10)),
                text(column(node, 3)),
                text(column(node, 12)),
                text(column(node, 26)),
                text(column(node, 4)),
                text(column(node, 5)),
                text(column(node, 6)),
                text(column(node, 7)),
                text(column(node, 16)),
                text(column(node, 8)),
                text(column(node, 9)),
                text(column(node, 18)),
                text(column(node, 25)),
                number(column(node, 17)),
                text(column(node, 27)),
                node.toString());
    }

    private record Booked(LocalDate date, Instant instant) {}

    /**
     * A number is epoch milliseconds, midnight in Prague. A string is a date and the offset its midnight is in, so
     * {@code 2015-03-30+0200} is 22:00 UTC on the 29th and still the 30th; without an offset, Prague's.
     */
    private static Booked booked(long id, JsonNode value) {
        if (value.isNumber()) {
            var instant = Instant.ofEpochMilli(value.longValue());
            return new Booked(instant.atZone(BANK_ZONE).toLocalDate(), instant);
        }
        var written = value.asString();
        try {
            var parsed = OFFSET_DATE.parse(written);
            var date = LocalDate.from(parsed);
            ZoneId zone = parsed.isSupported(ChronoField.OFFSET_SECONDS) ? ZoneOffset.from(parsed) : BANK_ZONE;
            return new Booked(date, date.atStartOfDay(zone).toInstant());
        } catch (DateTimeException e) {
            throw new FioException.InvalidResponse(
                    "Movement " + id + " has a date Fio never documented: " + written, e);
        }
    }

    /** The {@code value} of a column; null when the column is missing, {@code null}, or holds a {@code null}. */
    private static @Nullable JsonNode column(JsonNode node, int number) {
        var column = present(node.path("column" + number));
        return column == null ? null : present(column.path("value"));
    }

    private static @Nullable JsonNode present(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node;
    }

    /** Trimmed; null when blank, since Fio pads some empty fields with a single space. */
    private static @Nullable String text(@Nullable JsonNode node) {
        if (node == null || present(node) == null) {
            return null;
        }
        var value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }

    private static @Nullable Long number(@Nullable JsonNode node) {
        if (node == null || present(node) == null) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        try {
            return Long.valueOf(node.asString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Never through a double: a statement that rounds is a statement that is wrong. */
    private static @Nullable BigDecimal decimal(@Nullable JsonNode node) {
        if (node == null || present(node) == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
