package cz.insuit.fio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Against the JDK's own HTTP server on a random port: what matters is the URL built and what is made of the status
 * Fio answers with, since Fio's codes do not mean what they usually do.
 */
@DisplayName("Fio client")
class FioClientTest {

    private static final String EMPTY = "{\"accountStatement\":{\"info\":{\"accountId\":\"1\",\"bankId\":\"2010\","
            + "\"currency\":\"CZK\",\"iban\":\"x\",\"bic\":\"y\"},\"transactionList\":null}}";
    private static final FioToken TOKEN = new FioToken("t0ken");

    private final List<String> requested = new CopyOnWriteArrayList<>();
    private volatile String requestBody = "";
    private volatile String requestType = "";
    private volatile int status = 200;
    private volatile String body = EMPTY;
    private HttpServer server;
    private FioClient client;

    @BeforeEach
    void start() throws IOException, URISyntaxException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            requestType =
                    Objects.requireNonNullElse(exchange.getRequestHeaders().getFirst("Content-Type"), "");
            try (var in = exchange.getRequestBody()) {
                requestBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            // A body of zero length is sent as -1, or the exchange hangs.
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (var out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        var address = server.getAddress();
        // The seven-argument constructor brackets an IPv6 loopback.
        var base = new URI("http", null, address.getHostString(), address.getPort(), "/v1/rest", null, null);
        client = FioClient.builder().baseUri(base).build();
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    @DisplayName("the URLs match the documented templates, the token in the path")
    void urls() {
        client.transactionsSinceLastDownload(TOKEN);
        client.transactionsForPeriod(TOKEN, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 10));
        client.setLastId(TOKEN, 1147608196L);
        client.setLastDate(TOKEN, LocalDate.of(2026, 3, 1));

        assertEquals(
                List.of(
                        "GET /v1/rest/last/t0ken/transactions.json",
                        "GET /v1/rest/periods/t0ken/2026-01-05/2026-02-10/transactions.json",
                        "GET /v1/rest/set-last-id/t0ken/1147608196/",
                        "GET /v1/rest/set-last-date/t0ken/2026-03-01/"),
                requested);
    }

    @Test
    @DisplayName("each status means what Fio's documentation says, not what HTTP usually means")
    void statuses() {
        // 500 is the expensive one: a token lasts 180 days at most, so a dead token is every token's expected end,
        // and reading it as a server blip polls it forever without telling anybody.
        assertStatus(500, FioInvalidTokenException.class);
        assertStatus(409, FioRateLimitedException.class);
        assertStatus(413, FioTooManyItemsException.class);
        assertStatus(422, FioHistoryLockedException.class);
        assertStatus(404, FioBadRequestException.class);
        var other = assertStatus(418, FioHttpErrorException.class);
        assertEquals(418, ((FioHttpErrorException) other).status());
    }

    private FioApiException assertStatus(int code, Class<? extends FioApiException> expected) {
        status = code;
        body = "";
        return assertThrows(expected, () -> client.transactionsSinceLastDownload(TOKEN));
    }

    @Test
    @DisplayName("what Fio says in its error document is read, so a dead token can be told from an outage")
    void errorDocument() {
        status = 500;
        body = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><response><result><errorCode>21"
                + "</errorCode><status>error</status><message>Výpis neexistuje</message></result></response>";

        var thrown = assertThrows(FioInvalidTokenException.class, () -> client.transactionsSinceLastDownload(TOKEN));

        assertEquals(new FioError(21, "error", "Výpis neexistuje"), thrown.error());
    }

    @Test
    @DisplayName("an error document needs its message")
    void errorNeedsMessage() {
        assertThrows(NullPointerException.class, () -> new FioError(1, "error", nullString()));
    }

    @SuppressWarnings("NullAway")
    private static String nullString() {
        return null;
    }

    @Test
    @DisplayName("an error document that repeats the token hands it out masked")
    void errorDocumentMasked() {
        status = 500;
        body = "<response><result><errorCode>1</errorCode><status>error</status>"
                + "<message>Token t0ken neexistuje</message></result></response>";

        var thrown = assertThrows(FioInvalidTokenException.class, () -> client.transactionsSinceLastDownload(TOKEN));

        assertEquals(
                "Token *** neexistuje", Objects.requireNonNull(thrown.error()).message());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "token t0ken is not active",
                "<html><body>Bad gateway</body></html>",
                "<response><result><status>error</status></result></response>",
                "<response><unclosed>",
                // A DTD is refused outright, so an external entity cannot be read through the error body.
                "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                        + "<response><result><message>&x;</message></result></response>"
            })
    @DisplayName("a body that is not Fio's error document leaves error() empty and still throws by status")
    void notAnErrorDocument(String errorBody) {
        status = 500;
        body = errorBody;

        var thrown = assertThrows(FioInvalidTokenException.class, () -> client.transactionsSinceLastDownload(TOKEN));

        assertNull(thrown.error());
    }

    @Test
    @DisplayName("no failure message carries the token, which is in the URL")
    void tokenStaysOutOfMessages() {
        body = "token t0ken is not active";
        for (int code : new int[] {409, 500, 413, 422, 404, 418}) {
            status = code;
            var thrown = assertThrows(FioApiException.class, () -> client.transactionsSinceLastDownload(TOKEN));
            assertFalse(
                    String.valueOf(thrown.getMessage()).contains(TOKEN.value()),
                    code + " leaked the token: " + thrown.getMessage());
        }
    }

    @Test
    @DisplayName("an unreadable answer to the bookmark read says the bookmark may have moved")
    void unreadableSinceLast() {
        // Fio advances the bookmark for any answer holding movements, readable or not; a plain InvalidResponse would
        // read as "nothing to undo", and those movements would never be offered again.
        body = "{\"accountStatement\":{\"info\":{},\"transactionList\":{\"transaction\":[{\"column22\":{\"value\":7},"
                + "\"column0\":{\"value\":\"27.07.2012\"},\"column1\":{\"value\":1.00},\"column14\":{\"value\":\"CZK\"}}]}}}";

        var thrown =
                assertThrows(FioUnreadableSinceLastException.class, () -> client.transactionsSinceLastDownload(TOKEN));

        assertInstanceOf(FioInvalidResponseException.class, thrown.getCause());
        assertTrue(String.valueOf(thrown.getMessage()).contains("27.07.2012"), thrown.getMessage());
    }

    @Test
    @DisplayName("an unreadable answer to a period read is only invalid: that read moves no bookmark")
    void unreadablePeriod() {
        body = "not json";

        assertThrows(
                FioInvalidResponseException.class,
                () -> client.transactionsForPeriod(TOKEN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)));
    }

    @Test
    @DisplayName("a period that ends before it starts is refused before any request is made")
    void backwardsPeriod() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.transactionsForPeriod(TOKEN, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
        assertTrue(requested.isEmpty(), "a nonsense period still cost a request against the 30 seconds");
    }

    @Test
    @DisplayName("no answer is NoAnswer, and the observer hears why")
    void noAnswer() {
        var calls = new CopyOnWriteArrayList<FioCall>();
        server.stop(0);

        assertThrows(
                FioNoAnswerException.class,
                () -> client.transactionsForPeriod(
                        TOKEN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), calls::add));

        assertNull(calls.getFirst().status());
        assertNotNull(calls.getFirst().transportError());
    }

    @Test
    @DisplayName(
            "no answer to the bookmark read says the bookmark may have moved: a timeout can come after Fio served it")
    void noAnswerSinceLast() {
        server.stop(0);

        var thrown =
                assertThrows(FioUnreadableSinceLastException.class, () -> client.transactionsSinceLastDownload(TOKEN));

        assertInstanceOf(FioNoAnswerException.class, thrown.getCause());
    }

    @Test
    @DisplayName("every answer reaches the observer, the token masked and a successful body left out")
    void observer() {
        var calls = new CopyOnWriteArrayList<FioCall>();
        client.transactionsSinceLastDownload(TOKEN, calls::add);
        var ok = calls.getFirst();
        assertEquals(FioCallKind.TRANSACTIONS_SINCE_LAST, ok.kind());
        assertEquals(200, ok.status());
        assertEquals(EMPTY.getBytes(StandardCharsets.UTF_8).length, ok.responseBytes());
        // A successful body holds the account's movements: only its size is reported.
        assertNull(ok.errorBody());

        status = 500;
        body = "token t0ken is not active";
        assertThrows(FioInvalidTokenException.class, () -> client.setLastId(TOKEN, 1, calls::add));
        assertEquals(500, calls.getLast().status());
        assertEquals("token *** is not active", calls.getLast().errorBody());
    }

    @Test
    @DisplayName("an observer that throws does not change what the call returns")
    void observerThrows() {
        var statement = client.transactionsSinceLastDownload(TOKEN, call -> {
            throw new IllegalStateException("broken telemetry");
        });

        assertTrue(statement.transactions().isEmpty());
    }

    @Test
    @DisplayName("an error excerpt masks the token before cutting, so no fragment survives at the edge")
    void excerpt() {
        var body = "x".repeat(997) + TOKEN.value();

        assertEquals("x".repeat(997) + "***", FioClient.errorExcerpt(body, TOKEN));
    }

    @Test
    @DisplayName("an import posts Fio's form, token, type and the orders as a file, and reads the answer")
    void importsPayments() {
        body = "<responseImport><result><errorCode>0</errorCode><idInstruction>7</idInstruction>"
                + "<status>ok</status></result></responseImport>";

        var result = client.importPayments(TOKEN, List.of(FioImportXmlTest.FULL));

        assertEquals(List.of("POST /v1/rest/import/"), requested);
        assertTrue(requestType.startsWith("multipart/form-data; boundary="), requestType);
        var boundary = requestType.substring(requestType.indexOf('=') + 1);
        var part = "--" + boundary + "\r\nContent-Disposition: form-data; name=";
        assertTrue(requestBody.startsWith(part + "\"token\"\r\n\r\nt0ken\r\n"), requestBody);
        assertTrue(requestBody.contains(part + "\"type\"\r\n\r\nxml\r\n"), requestBody);
        assertTrue(requestBody.contains(part + "\"file\"; filename=\"orders.xml\""), requestBody);
        assertTrue(requestBody.contains("<amount>123.45</amount>"), requestBody);
        assertTrue(requestBody.endsWith("--" + boundary + "--\r\n"), requestBody);
        assertTrue(result.accepted());
        assertEquals(7L, result.instructionId());
    }

    @Test
    @DisplayName("an import shares the status codes of a read: 409 is the 30 seconds")
    void importRateLimited() {
        status = 409;
        body = "";

        assertThrows(FioRateLimitedException.class, () -> client.importPayments(TOKEN, List.of(FioImportXmlTest.FULL)));
    }

    @Test
    @DisplayName("an empty import is refused before any request")
    void importNothing() {
        assertThrows(IllegalArgumentException.class, () -> client.importPayments(TOKEN, List.of()));
        assertEquals(List.of(), requested);
    }
}
