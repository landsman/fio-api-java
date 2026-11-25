package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Reads a Fio account through <i>API Bankovnictví</i>, as documented in
 * <a href="https://www.fio.cz/docs/cz/API_Bankovnictvi.pdf">API_Bankovnictvi.pdf</a> (v1.9, 16 October 2025).
 *
 * <p><b>Read-only by design.</b> Fio issues a token that only reads (<i>sledování účtu</i>) and one that can also
 * send payment orders. This client has no way to send anything, so ask the account holder for the first.
 *
 * <p><b>One request per token per 30 seconds</b>, reads and writes together, or Fio answers 409; {@link
 * TokenThrottle} keeps a caller under it.
 *
 * <p>One instance serves any number of accounts: the token is passed with each call and never kept. Thread-safe.
 * {@link #close()} releases the HTTP client it made itself, not one passed to {@link Builder#httpClient}.
 */
public final class FioClient implements AutoCloseable {

    /** Fio's host since 30 November 2024; {@code https://www.fio.cz/ib_api/} before it is switched off. */
    public static final URI PRODUCTION = URI.create("https://fioapi.fio.cz/v1/rest/");

    private static final int ERROR_BODY_LIMIT = 1_000;

    private final HttpClient http;
    private final boolean ownsHttp;
    private final URI baseUri;
    private final Duration requestTimeout;

    /** A client for production. */
    public FioClient() {
        this(builder());
    }

    private FioClient(Builder builder) {
        this.ownsHttp = builder.http == null;
        this.http = builder.http != null
                ? builder.http
                : HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var base = builder.baseUri.toString();
        this.baseUri = URI.create(base.endsWith("/") ? base : base + "/");
        this.requestTimeout = builder.requestTimeout;
    }

    /** Starts a client for production; set only what differs. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() {
        if (ownsHttp) {
            http.close();
        }
    }

    /** As {@link #transactionsSinceLastDownload(FioToken, Consumer)}, telling nobody about the call. */
    public FioAccountStatement transactionsSinceLastDownload(FioToken token) {
        return transactionsSinceLastDownload(token, FioClient::ignore);
    }

    /**
     * Everything booked since the token's last read, as far as Fio's bookmark says.
     *
     * <p><b>This moves the bookmark.</b> Fio keeps the id of the last movement read per token, and an answer that holds
     * movements advances it, before the caller has done anything with them. So <b>store what came back before
     * treating the read as done</b>, and when storing fails, wind the bookmark back with {@link #setLastId} to the
     * last movement stored. An answer with no movements leaves it alone.
     *
     * @param observer hears about the request whatever its outcome, for the caller's own telemetry; whatever it throws
     *     is ignored
     * @throws FioException.UnreadableSinceLast when Fio answered but the answer cannot be read; the bookmark may have
     *     moved, wind it back
     * @throws FioException when Fio refuses or fails; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public FioAccountStatement transactionsSinceLastDownload(FioToken token, Consumer<FioCall> observer) {
        var body = get(
                token, "last/" + token.value() + "/transactions.json", FioCallKind.TRANSACTIONS_SINCE_LAST, observer);
        try {
            return FioStatementParser.parse(body);
        } catch (FioException.InvalidResponse e) {
            throw new FioException.UnreadableSinceLast(e.getMessage() + "; Fio may have moved its bookmark past it", e);
        }
    }

    /** As {@link #transactionsForPeriod(FioToken, LocalDate, LocalDate, Consumer)}, telling nobody about the call. */
    public FioAccountStatement transactionsForPeriod(FioToken token, LocalDate from, LocalDate to) {
        return transactionsForPeriod(token, from, to, FioClient::ignore);
    }

    /**
     * Everything booked from {@code from} to {@code to}, both inclusive, leaving the bookmark alone. For a backfill,
     * and for movements a failed read lost. Older than 90 days needs the account holder's unlock in internet banking.
     *
     * @throws IllegalArgumentException when {@code to} is before {@code from}; no request is made
     * @throws FioException.HistoryLocked when the period reaches past 90 days without the unlock
     * @throws FioException when Fio refuses, fails or answers in a shape this library cannot read; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public FioAccountStatement transactionsForPeriod(
            FioToken token, LocalDate from, LocalDate to, Consumer<FioCall> observer) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to " + to + " is before from " + from);
        }
        var path = "periods/" + token.value() + "/" + from + "/" + to + "/transactions.json";
        return FioStatementParser.parse(get(token, path, FioCallKind.TRANSACTIONS_FOR_PERIOD, observer));
    }

    /** As {@link #setLastId(FioToken, long, Consumer)}, telling nobody about the call. */
    public void setLastId(FioToken token, long lastId) {
        setLastId(token, lastId, FioClient::ignore);
    }

    /**
     * Moves the bookmark to the movement {@code lastId}, so the next {@link #transactionsSinceLastDownload} returns
     * what came after it. It counts against the same 30 seconds as a read.
     *
     * @throws FioException when Fio refuses or fails; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public void setLastId(FioToken token, long lastId, Consumer<FioCall> observer) {
        get(token, "set-last-id/" + token.value() + "/" + lastId + "/", FioCallKind.SET_LAST_ID, observer);
    }

    /** As {@link #setLastDate(FioToken, LocalDate, Consumer)}, telling nobody about the call. */
    public void setLastDate(FioToken token, LocalDate date) {
        setLastDate(token, date, FioClient::ignore);
    }

    /**
     * Moves the bookmark to a day: the next {@link #transactionsSinceLastDownload} returns what was booked after it.
     *
     * @throws FioException when Fio refuses or fails; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public void setLastDate(FioToken token, LocalDate date, Consumer<FioCall> observer) {
        get(token, "set-last-date/" + token.value() + "/" + date + "/", FioCallKind.SET_LAST_DATE, observer);
    }

    /** No message names the URL: the token is in it, and a message ends up in logs, tickets and screenshots. */
    private String get(FioToken token, String path, FioCallKind kind, Consumer<FioCall> observer) {
        requireNonNull(observer, "observer");
        var request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        var what = kind.name();
        long started = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | UncheckedIOException e) {
            report(
                    observer,
                    new FioCall(
                            kind,
                            null,
                            millisSince(started),
                            null,
                            null,
                            e.getClass().getSimpleName()));
            throw new FioException.NoAnswer(what + " did not get an answer", e);
        } catch (InterruptedException e) {
            // Whoever owns the thread asked it to stop; that is not Fio failing, so not a FioException a retry catches.
            Thread.currentThread().interrupt();
            var cancelled = new CancellationException(what + " was interrupted");
            cancelled.initCause(e);
            throw cancelled;
        }
        int status = response.statusCode();
        var body = response.body();
        boolean ok = status / 100 == 2;
        report(
                observer,
                new FioCall(
                        kind,
                        status,
                        millisSince(started),
                        body.getBytes(StandardCharsets.UTF_8).length,
                        ok ? null : errorExcerpt(body, token),
                        null));
        if (ok) {
            return body;
        }
        var error = FioError.parse(body);
        var message = what + " answered " + status;
        throw switch (status) {
            case 500 -> new FioException.InvalidToken(message + ": the token does not exist or is not active", error);
            case 409 -> new FioException.RateLimited(message + ": one request per token per 30 seconds", error);
            case 413 -> new FioException.TooManyItems(message + ": more than 50,000 movements", error);
            case 422 -> new FioException.HistoryLocked(message + ": older than 90 days needs unlocking", error);
            case 404 -> new FioException.BadRequest(message + ": Fio did not recognise the request", error);
            default -> new FioException.HttpError(message, status, error);
        };
    }

    /** The observer of a call nobody asked to hear about. */
    private static void ignore(FioCall call) {}

    /** Recording a call must never change what the call returned or threw. */
    @SuppressWarnings("EmptyCatch")
    private static void report(Consumer<FioCall> observer, FioCall call) {
        try {
            observer.accept(call);
        } catch (RuntimeException ignored) {
            // The caller's telemetry failed; the call itself did not.
        }
    }

    /** The start of an error body, the token masked before cutting so no fragment of it survives at the edge. */
    static String errorExcerpt(String body, FioToken token) {
        var masked = body.replace(token.value(), "***");
        return masked.length() <= ERROR_BODY_LIMIT ? masked : masked.substring(0, ERROR_BODY_LIMIT);
    }

    private static long millisSince(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    /** What differs from production. */
    public static final class Builder {
        private URI baseUri = PRODUCTION;
        private Duration requestTimeout = Duration.ofSeconds(60);
        private @Nullable HttpClient http;

        private Builder() {}

        /** Where Fio's API lives; {@link #PRODUCTION} unless a test points it at its own server. */
        public Builder baseUri(URI baseUri) {
            this.baseUri = requireNonNull(baseUri, "baseUri");
            return this;
        }

        /** How long one request may take, answer included; 60 seconds unless set. */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requireNonNull(requestTimeout, "requestTimeout");
            return this;
        }

        /** An HTTP client of the caller's, which {@link FioClient#close()} leaves open. */
        public Builder httpClient(HttpClient http) {
            this.http = requireNonNull(http, "http");
            return this;
        }

        public FioClient build() {
            return new FioClient(this);
        }
    }
}
