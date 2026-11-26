package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/** HTTP 413: more than 50,000 movements matched. Narrow the period, or move the bookmark forward. */
public final class FioTooManyItemsException extends FioApiException {
    public FioTooManyItemsException(String message, @Nullable FioError error) {
        super(message, error, null);
    }
}
