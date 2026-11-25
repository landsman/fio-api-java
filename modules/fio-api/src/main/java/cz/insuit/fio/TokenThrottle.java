package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one token's requests at least 30 seconds apart, because Fio answers 409 when they are not, reads and writes
 * counted together. Asking anyway and handling the 409 works too, but it spends the request the next caller needed:
 * a poller that retries on 409 can starve itself while looking busy.
 *
 * <p>In memory, so it sees only the requests of this JVM. Two instances polling one token still meet a 409 now and
 * then; read it as "another instance got there first".
 */
public final class TokenThrottle {

    /** The documentation's minimum, and the interval a 409 is measured against. */
    public static final Duration DEFAULT_MINIMUM_INTERVAL = Duration.ofSeconds(30);

    private final ConcurrentHashMap<FioToken, Instant> lastCall = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration minimumInterval;

    /** Thirty seconds on the system clock. */
    public TokenThrottle() {
        this(Clock.systemUTC(), DEFAULT_MINIMUM_INTERVAL);
    }

    public TokenThrottle(Clock clock, Duration minimumInterval) {
        this.clock = requireNonNull(clock, "clock");
        this.minimumInterval = requireNonNull(minimumInterval, "minimumInterval");
    }

    /**
     * Whether {@code token} may be used now. Records the attempt when it may, so two callers racing on one token
     * cannot both be told yes.
     */
    public boolean tryAcquire(FioToken token) {
        var now = clock.instant();
        var previous = lastCall.putIfAbsent(token, now);
        if (previous == null) {
            return true;
        }
        if (Duration.between(previous, now).compareTo(minimumInterval) < 0) {
            return false;
        }
        // Only the caller that swaps the old value out proceeds: replace() is the compare-and-set.
        return lastCall.replace(token, previous, now);
    }

    /** How long until {@code token} may be used again; zero when it may be used now. */
    public Duration waitFor(FioToken token) {
        var previous = lastCall.get(token);
        if (previous == null) {
            return Duration.ZERO;
        }
        var left = minimumInterval.minus(Duration.between(previous, clock.instant()));
        return left.isNegative() ? Duration.ZERO : left;
    }
}
