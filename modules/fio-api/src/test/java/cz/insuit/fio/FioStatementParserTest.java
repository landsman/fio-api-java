package cz.insuit.fio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Against the examples in Fio's documentation (v1.9, 16 October 2025) and responses captured by other people's
 * clients, in {@code src/test/resources/fio/}: a null column, an empty statement, a movement with no counterparty
 * are what a real account produces eventually and never on demand.
 */
@DisplayName("Fio statement parsing")
class FioStatementParserTest {

    private static String fixture(String name) {
        try (var in = FioStatementParserTest.class.getResourceAsStream("/fio/" + name + ".json")) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("the header and every movement come through, columns mapped to names")
    void period() {
        var statement = FioStatementParser.parse(fixture("period"));

        assertEquals("2400222222", statement.info().accountId());
        assertEquals("CZ7920100000002400222222", statement.info().iban());
        assertEquals("FIOBCZPPXXX", statement.info().bic());
        assertEquals(1149190192L, statement.info().idLastDownload());
        assertEquals(3, statement.transactions().size());

        var first = statement.transactions().getFirst();
        assertEquals(1148734530L, first.id());
        assertEquals("CZK", first.currency());
        assertEquals("2900233333", first.counterpartyAccount());
        assertEquals("Pavel, Novák", first.counterpartyName());
        assertEquals("Fio banka, a.s.", first.counterpartyBankName());
        assertEquals("Příjem převodem uvnitř banky", first.type());
        assertEquals(2105685816L, first.instructionId());
    }

    @Test
    @DisplayName("an epoch-millisecond date is midnight in Prague")
    void epochMillis() {
        var first = FioStatementParser.parse(fixture("period")).transactions().getFirst();

        assertEquals(LocalDate.of(2012, 6, 26), first.date());
        assertEquals(1340661600000L, first.bookedAt().toEpochMilli());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        // Captured in the wild, and the documentation's field table.
        "2015-03-30+0200, 2015-03-30, 2015-03-29T22:00:00Z",
        // Fio's XML, and what an ISO formatter writes.
        "2015-03-30+02:00, 2015-03-30, 2015-03-29T22:00:00Z",
        "2015-03-30Z, 2015-03-30, 2015-03-30T00:00:00Z",
        // Winter: Prague is +01:00.
        "2015-01-30+0100, 2015-01-30, 2015-01-29T23:00:00Z",
        // No offset: Prague's midnight.
        "2015-07-01, 2015-07-01, 2015-06-30T22:00:00Z"
    })
    @DisplayName("a string date is the day written, its midnight in the offset given")
    void stringDates(String written, LocalDate date, Instant bookedAt) {
        var t = FioStatementParser.parse(movementDated("\"" + written + "\""))
                .transactions()
                .getFirst();

        assertEquals(date, t.date());
        assertEquals(bookedAt, t.bookedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"27.07.2012\"", "\"2015-03-30T10:00:00+0200\"", "\"\"", "true"})
    @DisplayName("a date in no documented shape is refused, the value in the message")
    void unknownDate(String written) {
        var thrown = assertThrows(
                FioException.InvalidResponse.class, () -> FioStatementParser.parse(movementDated(written)));

        assertTrue(String.valueOf(thrown.getMessage()).contains("date"), thrown.getMessage());
    }

    private static String movementDated(String date) {
        return "{\"accountStatement\":{\"info\":{},\"transactionList\":{\"transaction\":[{"
                + "\"column22\":{\"value\":7},\"column0\":{\"value\":" + date + "},"
                + "\"column1\":{\"value\":1.00},\"column14\":{\"value\":\"CZK\"}}]}}}";
    }

    @Test
    @DisplayName("money keeps its value and scale, no double anywhere on the way in")
    void exactAmounts() {
        var statement = FioStatementParser.parse(fixture("period"));
        var transactions = statement.transactions();

        // BigDecimal.equals compares scale too, so this fails if trailing zeros were stripped.
        assertEquals(new BigDecimal("1.00"), transactions.get(0).amount());
        assertEquals(new BigDecimal("-1.00"), transactions.get(1).amount());
        assertEquals(new BigDecimal("0.01"), transactions.get(2).amount());
        assertEquals(new BigDecimal("195.00"), statement.info().openingBalance());
    }

    @Test
    @DisplayName("an amount a double cannot hold arrives whole")
    void beyondDouble() {
        // 19 significant digits: through a double it would come back rounded.
        var json = movementDated("1340661600000").replace("1.00", "1234567890123456.78");

        assertEquals(
                new BigDecimal("1234567890123456.78"),
                FioStatementParser.parse(json).transactions().getFirst().amount());
    }

    @Test
    @DisplayName("the symbols a payment is paired on keep their leading zeros")
    void symbols() {
        var t = FioStatementParser.parse(fixture("with-symbols")).transactions().getFirst();

        assertEquals("0001", t.variableSymbol());
        assertEquals("0002", t.specificSymbol());
        assertEquals("0558", t.constantSymbol());
        assertEquals(new BigDecimal("-15.00"), t.amount());
    }

    @Test
    @DisplayName("a null column is absent, and so is one Fio padded with a space")
    void absentColumns() {
        var transactions = FioStatementParser.parse(fixture("period")).transactions();
        var interest = transactions.getLast();

        assertNull(interest.counterpartyAccount());
        assertNull(interest.counterpartyBankCode());
        assertNull(interest.variableSymbol());
        assertNull(transactions.get(1).comment());
        assertNull(transactions.get(1).userIdentification());
    }

    @Test
    @DisplayName("each movement keeps the JSON Fio sent")
    void rawJson() {
        var first = FioStatementParser.parse(fixture("period")).transactions().getFirst();

        assertTrue(first.rawJson().contains("1148734530"));
        assertTrue(first.rawJson().contains("Pavel, Novák"));
        assertEquals(
                first.id(),
                FioStatementParser.parse(wrap(first.rawJson()))
                        .transactions()
                        .getFirst()
                        .id());
    }

    private static String wrap(String movement) {
        return "{\"accountStatement\":{\"info\":{},\"transactionList\":{\"transaction\":[" + movement + "]}}}";
    }

    @Test
    @DisplayName("toString leaves out the people and their accounts")
    void toStringRedacts() {
        var first = FioStatementParser.parse(fixture("period")).transactions().getFirst();

        assertEquals("FioTransaction[id=1148734530, date=2012-06-26, amount=1.00 CZK]", first.toString());
    }

    @Test
    @DisplayName("a quiet account is an empty list, in both shapes Fio sends it")
    void empty() {
        var documented = FioStatementParser.parse(fixture("empty"));
        assertTrue(documented.transactions().isEmpty());
        assertEquals(1150392361L, documented.info().idLastDownload());

        var captured = FioStatementParser.parse(fixture("real-world-empty-array"));
        assertTrue(captured.transactions().isEmpty());
        assertEquals("EUR", captured.info().currency());
    }

    @Test
    @DisplayName("a captured real response parses, its dates strings")
    void realWorld() {
        var statement = FioStatementParser.parse(fixture("real-world-string-dates"));

        assertEquals("CZ7920100000002400222222", statement.info().iban());
        assertEquals(3, statement.transactions().size());
        var first = statement.transactions().getFirst();
        assertEquals(LocalDate.of(2015, 3, 30), first.date());
        assertEquals(new BigDecimal("127.00"), first.amount());
        assertEquals(Instant.parse("2015-03-29T22:00:00Z"), first.bookedAt());
    }

    @Test
    @DisplayName("a missing or null list of movements is a quiet account")
    void emptyShapes() {
        for (var list : new String[] {
            "", ",\"transactionList\":null", ",\"transactionList\":{}", ",\"transactionList\":{\"transaction\":null}"
        }) {
            var body = "{\"accountStatement\":{\"info\":{}" + list + "}}";
            assertTrue(FioStatementParser.parse(body).transactions().isEmpty(), body);
        }
    }

    @Test
    @DisplayName("a movement where a list belongs is refused, not read as a quiet account")
    void movementsNotAList() {
        var single = movementDated("1340661600000").replace("[{", "{").replace("}]", "}");

        var thrown = assertThrows(FioException.InvalidResponse.class, () -> FioStatementParser.parse(single));

        assertTrue(String.valueOf(thrown.getMessage()).contains("not a list"), thrown.getMessage());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "\"column1\":{\"value\":1.00} | \"column1\":{\"value\":\"1,00\"} | 1,00",
                "\"column22\":{\"value\":7} | \"column22\":{\"value\":\"7a\"} | 7a",
                "\"column1\":{\"value\":1.00} | \"column1\":{\"value\":1.00},\"column17\":{\"value\":\"x1\"} | x1"
            })
    @DisplayName("a number that is there but unreadable is refused with its value, not passed off as absent")
    void unreadableNumbers(String from, String to, String shown) {
        var body = movementDated("1340661600000").replace(from, to);

        var thrown = assertThrows(FioException.InvalidResponse.class, () -> FioStatementParser.parse(body));

        assertTrue(String.valueOf(thrown.getMessage()).contains(shown), thrown.getMessage());
    }

    @Test
    @DisplayName("an unreadable balance in the header is refused too")
    void unreadableBalance() {
        var body = movementDated("1340661600000").replace("\"info\":{}", "\"info\":{\"closingBalance\":\"n/a\"}");

        assertThrows(FioException.InvalidResponse.class, () -> FioStatementParser.parse(body));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "not json at all",
                "{}",
                "{\"accountStatement\":{}}",
                "{\"accountStatement\":{\"info\":{},\"transactionList\":{\"transaction\":[{\"column0\":{\"value\":1}}]}}}"
            })
    @DisplayName("something that is not a statement is refused, not half-read")
    void refused(String body) {
        assertThrows(FioException.InvalidResponse.class, () -> FioStatementParser.parse(body));
    }
}
