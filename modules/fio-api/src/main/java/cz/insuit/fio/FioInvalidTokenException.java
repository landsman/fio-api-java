package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * HTTP 500: the token does not exist or is no longer active, as Fio documents it. Usually not a server fault: a
 * token lasts 180 days at most, so this is the expected end of every token's life. Tell the account holder rather
 * than retry for ever.
 *
 * <p>Fio has also been seen answering 500 while its API was down, so one 500 is not proof. Before telling the
 * holder or switching the connection off, wait for the refusal to last, such as a few hours of polls without a
 * success in between.
 */
public final class FioInvalidTokenException extends FioApiException {
    public FioInvalidTokenException(String message, @Nullable FioError error) {
        super(message, error, null);
    }
}
