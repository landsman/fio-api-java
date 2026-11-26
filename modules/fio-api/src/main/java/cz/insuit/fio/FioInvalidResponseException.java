package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * A successful answer this library cannot read: not JSON, or a statement without a header, or a movement without
 * its id, date or amount. Nothing to undo: the call that got it does not move the bookmark. After {@link
 * FioClient#importPayments} it means the batch may have been taken: look in internet banking before sending it again.
 */
public final class FioInvalidResponseException extends FioApiException {
    public FioInvalidResponseException(String message, @Nullable Throwable cause) {
        super(message, null, cause);
    }
}
