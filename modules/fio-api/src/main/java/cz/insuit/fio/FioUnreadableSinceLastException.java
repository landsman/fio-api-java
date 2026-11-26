package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * {@link FioClient#transactionsSinceLastDownload} got an answer this library cannot read, or no whole answer: a
 * timeout or a cut connection may come after Fio served the read. <b>Fio may already have moved its bookmark past
 * the movements in it</b>, as it does for every answer that holds any, so the next read starts after them and they
 * are never offered again. Wind the bookmark back with {@link FioClient#setLastId} to the last movement stored, or
 * read them with {@link FioClient#transactionsForPeriod}. Either is a request, so <b>wait 30 seconds after the
 * failed read</b> or Fio answers 409; {@link TokenThrottle} says when. The cause is the {@link FioInvalidResponseException} or
 * {@link FioNoAnswerException} this replaces.
 */
public final class FioUnreadableSinceLastException extends FioApiException {
    public FioUnreadableSinceLastException(String message, @Nullable Throwable cause) {
        super(message, null, cause);
    }
}
