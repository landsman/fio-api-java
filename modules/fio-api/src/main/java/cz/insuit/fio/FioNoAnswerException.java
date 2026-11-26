package cz.insuit.fio;

/**
 * No answer came: a timeout or a refused connection. An interrupted thread is not one: that is a {@link
 * java.util.concurrent.CancellationException}, so a retry does not repeat work someone cancelled.
 */
public final class FioNoAnswerException extends FioApiException {
    public FioNoAnswerException(String message, Throwable cause) {
        super(message, null, cause);
    }
}
