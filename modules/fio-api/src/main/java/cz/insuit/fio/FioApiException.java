package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * A call to Fio that did not return what was asked for, one subclass per thing a caller does about it, each in its
 * own file, so a {@code switch} over them is exhaustive. The message never carries the token, which Fio takes in the URL
 * path or in the import's form, nor Fio's response body.
 *
 * <p>Fio's status codes do not mean what they usually do, and reading them as if they did is how a poller ends up
 * retrying a dead token forever: 500 is a dead token, 409 is the rate limit, 422 is history that needs unlocking.
 *
 * <p>The subclasses' constructors are public so a caller's own tests can throw them from a fake; the hierarchy stays
 * closed.
 */
public abstract sealed class FioApiException extends RuntimeException
        permits FioInvalidTokenException,
                FioRateLimitedException,
                FioTooManyItemsException,
                FioHistoryLockedException,
                FioBadRequestException,
                FioHttpErrorException,
                FioNoAnswerException,
                FioInvalidResponseException,
                FioUnreadableSinceLastException {

    private final @Nullable FioError error;

    FioApiException(String message, @Nullable FioError error, @Nullable Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    /** What Fio said in its error document, when it sent one; see {@link FioError}. */
    public @Nullable FioError error() {
        return error;
    }
}
