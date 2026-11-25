package cz.insuit.fio;

import org.jspecify.annotations.Nullable;

/**
 * One request to Fio as a caller may record it: what was asked, how it was answered and how long it took. It holds
 * nothing of the account: the token is masked out of {@link #errorBody}, and a successful body, which holds the
 * movements, is left out, only its size kept.
 *
 * @param status the HTTP status, or null when no response arrived (see {@link #transportError})
 * @param responseBytes the size of the body in bytes; null when no response arrived
 * @param errorBody the start of the body of an answer outside 2xx, the token masked; null for a 2xx
 * @param transportError the exception's class name when no response arrived, such as {@code HttpTimeoutException}
 */
public record FioCall(
        FioCallKind kind,
        @Nullable Integer status,
        long durationMillis,
        @Nullable Integer responseBytes,
        @Nullable String errorBody,
        @Nullable String transportError) {}
