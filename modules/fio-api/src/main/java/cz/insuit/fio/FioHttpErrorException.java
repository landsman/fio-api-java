package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/** Any other status that is not a success. */
public final class FioHttpErrorException extends FioApiException {
    private final int status;

    public FioHttpErrorException(String message, int status, @Nullable FioError error) {
        super(message, error, null);
        this.status = status;
    }

    /** The HTTP status Fio answered with. */
    public int status() {
        return status;
    }
}
