package cz.insuit.fio.spring;

import static java.util.Objects.requireNonNull;

import cz.insuit.fio.FioClient;
import cz.insuit.fio.FioToken;
import cz.insuit.fio.TokenThrottle;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * One {@link FioClient} and one {@link TokenThrottle} for the application, built from {@code fio.*}, unless the
 * application defines its own. Spring closes the client on shutdown, which releases the HTTP client it made.
 */
@AutoConfiguration
@EnableConfigurationProperties(FioProperties.class)
public class FioAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FioClient fioClient(FioProperties properties) {
        // Only what the application set is passed on, so each default lives in one place: the client.
        var builder = FioClient.builder();
        var baseUri = properties.baseUri();
        if (baseUri != null) {
            builder.baseUri(baseUri);
        }
        var requestTimeout = properties.requestTimeout();
        if (requestTimeout != null) {
            builder.requestTimeout(requestTimeout);
        }
        return builder.build();
    }

    /**
     * Shared, because the 30 seconds are per token across the whole application: one throttle per caller would let
     * each believe it had the token to itself. Any {@code TokenThrottle} bean of the application's replaces it,
     * such as a {@code TokenThrottle<String>} that also guards another bank's tokens. Spring matches the type
     * argument, so that application then has no {@code TokenThrottle<FioToken>}: it injects its own throttle by its
     * own type.
     */
    @Bean
    @ConditionalOnMissingBean(TokenThrottle.class)
    public TokenThrottle<FioToken> fioTokenThrottle() {
        return new TokenThrottle<>();
    }

    /**
     * Only with {@code fio.token} set; one holding anything but letters and digits, such as the newline an
     * environment variable often ends in, stops the application.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("fio.token")
    public FioToken fioToken(FioProperties properties) {
        return new FioToken(requireNonNull(properties.token(), "fio.token"));
    }
}
