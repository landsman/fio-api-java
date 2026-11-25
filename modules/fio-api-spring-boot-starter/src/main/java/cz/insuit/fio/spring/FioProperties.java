package cz.insuit.fio.spring;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code fio.*}.
 *
 * @param token one account's token, for an application that reads only that one; it then gets a {@code FioToken}
 *     bean. An application serving many accounts leaves it unset and passes each one's token with the call. Set it
 *     from the environment, never in a committed file.
 * @param baseUri Fio's API; the client's own default, production, unless set, such as to a test's mock server
 * @param requestTimeout how long one call to Fio may take; the client's own default unless set
 */
@ConfigurationProperties("fio")
public record FioProperties(
        @Nullable String token,
        @Nullable URI baseUri,
        @Nullable Duration requestTimeout) {}
