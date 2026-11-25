package cz.insuit.fio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("token throttle")
class TokenThrottleTest {

    private static final FioToken A = new FioToken("aaa");
    private static final FioToken B = new FioToken("bbb");

    private final MutableClock clock = new MutableClock();
    private final TokenThrottle throttle = new TokenThrottle(clock, Duration.ofSeconds(30));

    @Test
    @DisplayName("a token is let through once per 30 seconds, and others are not held up by it")
    void perToken() {
        assertTrue(throttle.tryAcquire(A));
        assertFalse(throttle.tryAcquire(A));
        assertTrue(throttle.tryAcquire(B));

        clock.advance(Duration.ofSeconds(29));
        assertFalse(throttle.tryAcquire(A));
        assertEquals(Duration.ofSeconds(1), throttle.waitFor(A));

        clock.advance(Duration.ofSeconds(1));
        assertEquals(Duration.ZERO, throttle.waitFor(A));
        assertTrue(throttle.tryAcquire(A));
        assertFalse(throttle.tryAcquire(A));
    }

    @Test
    @DisplayName("a token never used needs no wait")
    void unused() {
        assertEquals(Duration.ZERO, throttle.waitFor(A));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-25T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
