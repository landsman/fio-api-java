package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * HTTP 409: the 30-second minimum interval for this token was not kept; reads and writes count together. Retry
 * later, and keep calls apart with {@link TokenThrottle} so it rarely happens.
 */
public final class FioRateLimitedException extends FioApiException {
    public FioRateLimitedException(String message, @Nullable FioError error) {
        super(message, error, null);
    }
}
