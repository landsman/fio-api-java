package cz.insuit.fio.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import cz.insuit.fio.FioClient;
import cz.insuit.fio.FioException;
import cz.insuit.fio.FioToken;
import cz.insuit.fio.TokenThrottle;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayName("Fio auto-configuration")
class FioAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FioAutoConfiguration.class));

    @Test
    @DisplayName("with nothing set: a client for production and a throttle, no token")
    void defaults() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FioClient.class);
            assertThat(context).hasSingleBean(TokenThrottle.class);
            assertThat(context).doesNotHaveBean(FioToken.class);
        });
    }

    @Test
    @DisplayName("fio.base-uri and fio.request-timeout bind")
    void properties() {
        runner.withPropertyValues("fio.base-uri=http://localhost:8089/v1/rest", "fio.request-timeout=5s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(FioProperties.class);
                    assertThat(properties.baseUri()).hasToString("http://localhost:8089/v1/rest");
                    assertThat(properties.requestTimeout()).hasSeconds(5);
                });
    }

    @Test
    @DisplayName("fio.base-uri reaches the client: the call goes to that server")
    void baseUriReachesClient() throws IOException, URISyntaxException {
        var requested = new CopyOnWriteArrayList<String>();
        var server = server(exchange -> {
            requested.add(exchange.getRequestURI().getPath());
            respond(exchange, EMPTY);
        });
        try {
            runner.withPropertyValues("fio.base-uri=" + baseUri(server))
                    .run(context -> context.getBean(FioClient.class).transactionsForPeriod(TOKEN, DAY, DAY));
        } finally {
            server.stop(0);
        }

        assertThat(requested).containsExactly("/v1/rest/periods/abc123/2026-01-05/2026-01-05/transactions.json");
    }

    @Test
    @DisplayName("fio.request-timeout reaches the client: a slower answer is no answer")
    void requestTimeoutReachesClient() throws IOException, URISyntaxException {
        // Held until the test is done rather than slept through: server.stop waits for a running handler.
        var release = new CountDownLatch(1);
        var server = server(exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // An answer, not a dropped connection: without the timeout this call succeeds and the test fails.
            respond(exchange, EMPTY);
        });
        try {
            runner.withPropertyValues("fio.base-uri=" + baseUri(server), "fio.request-timeout=200ms")
                    .run(context -> assertThatThrownBy(
                                    () -> context.getBean(FioClient.class).transactionsForPeriod(TOKEN, DAY, DAY))
                            .isInstanceOf(FioException.NoAnswer.class));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    private static final FioToken TOKEN = new FioToken("abc123");
    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);
    private static final String EMPTY = "{\"accountStatement\":{\"info\":{},\"transactionList\":null}}";

    private static HttpServer server(HttpHandler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static String baseUri(HttpServer server) throws URISyntaxException {
        var address = server.getAddress();
        // The seven-argument constructor brackets an IPv6 loopback.
        return new URI("http", null, address.getHostString(), address.getPort(), "/v1/rest", null, null).toString();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    @DisplayName("fio.token gives a FioToken bean, and a malformed one stops the application")
    void token() {
        runner.withPropertyValues("fio.token=abc123").run(context -> {
            assertThat(context.getBean(FioToken.class).value()).isEqualTo("abc123");
            assertThat(context.getBean(FioProperties.class).toString())
                    .doesNotContain("abc123")
                    .contains("token=redacted");
        });
        runner.withPropertyValues("fio.token=abc/123")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("the application's own throttle, of any key, replaces the starter's")
    void ownThrottle() {
        runner.withUserConfiguration(SharedThrottle.class).run(context -> {
            assertThat(context).hasSingleBean(TokenThrottle.class);
            assertThat(context).hasBean("sharedThrottle");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class SharedThrottle {
        @Bean
        TokenThrottle<String> sharedThrottle() {
            return new TokenThrottle<>();
        }
    }
}
