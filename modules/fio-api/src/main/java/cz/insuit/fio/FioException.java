package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * A call to Fio that did not return what was asked for, one subtype per thing a caller does about it, so a {@code
 * switch} over them is exhaustive. The message never carries the token, which Fio takes in the URL path, nor Fio's
 * response body.
 *
 * <p>Fio's status codes do not mean what they usually do, and reading them as if they did is how a poller ends up
 * retrying a dead token forever: 500 is a dead token, 409 is the rate limit, 422 is history that needs unlocking.
 *
 * <p>The subtypes' constructors are public so a caller's own tests can throw them from a fake; the hierarchy stays
 * closed.
 */
public abstract sealed class FioException extends RuntimeException {

    private final @Nullable FioError error;

    FioException(String message, @Nullable FioError error, @Nullable Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    /** What Fio said in its error document, when it sent one; see {@link FioError}. */
    public @Nullable FioError error() {
        return error;
    }

    /**
     * HTTP 500: the token does not exist or is no longer active, as Fio documents it. Usually not a server fault: a
     * token lasts 180 days at most, so this is the expected end of every token's life. Tell the account holder rather
     * than retry for ever.
     *
     * <p>Fio has also been seen answering 500 while its API was down, so one 500 is not proof. Before telling the
     * holder or switching the connection off, wait for the refusal to last, such as a few hours of polls without a
     * success in between.
     */
    public static final class InvalidToken extends FioException {
        public InvalidToken(String message, @Nullable FioError error) {
            super(message, error, null);
        }
    }

    /**
     * HTTP 409: the 30-second minimum interval for this token was not kept; reads and writes count together. Retry
     * later, and keep calls apart with {@link TokenThrottle} so it rarely happens.
     */
    public static final class RateLimited extends FioException {
        public RateLimited(String message, @Nullable FioError error) {
            super(message, error, null);
        }
    }

    /** HTTP 413: more than 50,000 movements matched. Narrow the period, or move the bookmark forward. */
    public static final class TooManyItems extends FioException {
        public TooManyItems(String message, @Nullable FioError error) {
            super(message, error, null);
        }
    }

    /**
     * HTTP 422: movements older than 90 days, asked for without the unlock the account holder grants in internet
     * banking, which lasts ten minutes. Ask them, or stay within 90 days.
     */
    public static final class HistoryLocked extends FioException {
        public HistoryLocked(String message, @Nullable FioError error) {
            super(message, error, null);
        }
    }

    /** HTTP 404: Fio did not recognise the request. A fault in the caller or this library, not in the account. */
    public static final class BadRequest extends FioException {
        public BadRequest(String message, @Nullable FioError error) {
            super(message, error, null);
        }
    }

    /** Any other status that is not a success. */
    public static final class HttpError extends FioException {
        private final int status;

        public HttpError(String message, int status, @Nullable FioError error) {
            super(message, error, null);
            this.status = status;
        }

        /** The HTTP status Fio answered with. */
        public int status() {
            return status;
        }
    }

    /**
     * No answer came: a timeout or a refused connection. An interrupted thread is not one: that is a {@link
     * java.util.concurrent.CancellationException}, so a retry does not repeat work someone cancelled.
     */
    public static final class NoAnswer extends FioException {
        public NoAnswer(String message, Throwable cause) {
            super(message, null, cause);
        }
    }

    /**
     * A successful answer this library cannot read: not JSON, or a statement without a header, or a movement without
     * its id, date or amount. Nothing to undo: the call that got it does not move the bookmark.
     */
    public static final class InvalidResponse extends FioException {
        public InvalidResponse(String message, @Nullable Throwable cause) {
            super(message, null, cause);
        }
    }

    /**
     * {@link FioClient#transactionsSinceLastDownload} got an answer this library cannot read, or no whole answer: a
     * timeout or a cut connection may come after Fio served the read. <b>Fio may already have moved its bookmark past
     * the movements in it</b>, as it does for every answer that holds any, so the next read starts after them and they
     * are never offered again. Wind the bookmark back with {@link FioClient#setLastId} to the last movement stored, or
     * read them with {@link FioClient#transactionsForPeriod}. Either is a request, so <b>wait 30 seconds after the
     * failed read</b> or Fio answers 409; {@link TokenThrottle} says when. The cause is the {@link InvalidResponse} or
     * {@link NoAnswer} this replaces.
     */
    public static final class UnreadableSinceLast extends FioException {
        public UnreadableSinceLast(String message, @Nullable Throwable cause) {
            super(message, null, cause);
        }
    }
}
