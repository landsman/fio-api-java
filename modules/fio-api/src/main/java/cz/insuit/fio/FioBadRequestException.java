package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/** HTTP 404: Fio did not recognise the request. A fault in the caller or this library, not in the account. */
public final class FioBadRequestException extends FioApiException {
    public FioBadRequestException(String message, @Nullable FioError error) {
        super(message, error, null);
    }
}
