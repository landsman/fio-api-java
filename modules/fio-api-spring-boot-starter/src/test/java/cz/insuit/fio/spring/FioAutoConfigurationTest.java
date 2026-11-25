package cz.insuit.fio.spring;

import static org.assertj.core.api.Assertions.assertThat;

import cz.insuit.fio.FioClient;
import cz.insuit.fio.FioToken;
import cz.insuit.fio.TokenThrottle;
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
