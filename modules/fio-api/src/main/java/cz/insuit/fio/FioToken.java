package cz.insuit.fio;

import static java.util.Objects.requireNonNull;

/**
 * The token an account holder issues in Fio's internet banking. A type of its own so its {@code toString()} never
 * shows it: Fio takes the token in the URL path (or the import's form), so it is a credential that would otherwise end up in every log line
 * that prints a request.
 */
public record FioToken(String value) {

    /**
     * @throws IllegalArgumentException when the value is empty or holds anything but ASCII letters and digits, as a
     *     token read from a file or an environment variable often ends in a newline; the message does not repeat it
     */
    public FioToken {
        requireNonNull(value, "value");
        // It goes into the path: a slash or a dot would change which endpoint is called.
        if (value.isEmpty() || !value.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c))) {
            throw new IllegalArgumentException("The token is empty or holds something other than letters and digits");
        }
    }

    @Override
    public String toString() {
        return "FioToken[redacted]";
    }
}
