package cz.insuit.fio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Fio token")
class FioTokenTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "abc\n",
                "abc ",
                "abc ",
                "abc​",
                "abc﻿",
                "abc\t",
                // It goes into the path: these would call another endpoint.
                "abc/def",
                "..",
                "abc?x=1",
                "abc#",
                "ábc"
            })
    @DisplayName("anything but ASCII letters and digits is refused, without repeating the value")
    void refused(String value) {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new FioToken(value));

        assertFalse(!value.isBlank() && String.valueOf(thrown.getMessage()).contains(value.strip()));
    }

    @Test
    @DisplayName("toString never shows the token")
    void redacted() {
        var token = new FioToken("Secret123");

        assertEquals("FioToken[redacted]", token.toString());
        assertFalse(new FioClientCallHolder(token).toString().contains("Secret123"));
    }

    /** A record holding a token, as a caller's own would, logged whole. */
    private record FioClientCallHolder(FioToken token) {}
}
