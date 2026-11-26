package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * HTTP 422: movements older than 90 days, asked for without the unlock the account holder grants in internet
 * banking, which lasts ten minutes. Ask them, or stay within 90 days.
 */
public final class FioHistoryLockedException extends FioApiException {
    public FioHistoryLockedException(String message, @Nullable FioError error) {
        super(message, error, null);
    }
}
