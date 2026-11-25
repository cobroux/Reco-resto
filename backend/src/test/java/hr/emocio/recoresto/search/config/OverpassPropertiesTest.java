package hr.emocio.recoresto.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OverpassPropertiesTest {

    @EnableConfigurationProperties(OverpassProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    /** Raised from 60 s at the request of the team: the public instance answers in about 40 s when it answers. */
    @Test
    void theOverpassTimeoutLeavesTimeToASlowPublicInstance() {
        runner.run(context -> assertThat(context.getBean(OverpassProperties.class).timeoutSeconds()).isEqualTo(120));
    }

    @Test
    void aBadGatewayIsRetriedAfter5Then10Then20SecondsByDefault() {
        runner.run(context -> assertThat(context.getBean(OverpassProperties.class).retryDelaysSeconds())
                .containsExactly(5, 10, 20));
    }

    @Test
    void retryDelaysCanBeOverriddenOrDisabled() {
        runner.withPropertyValues("overpass.retry-delays-seconds=1,2").run(context -> assertThat(
                context.getBean(OverpassProperties.class).retryDelaysSeconds()).containsExactly(1, 2));
        runner.withPropertyValues("overpass.retry-delays-seconds=").run(context -> assertThat(
                context.getBean(OverpassProperties.class).retryDelaysSeconds()).isEmpty());
    }

    @Test
    void aSearchMakesAtMostOneOverpassCallByDefault() {
        runner.run(context -> assertThat(context.getBean(OverpassProperties.class).maxCalls()).isEqualTo(1));
    }

    /** The other values are placeholders not validated by the team (plan.md O11): only checked to be usable. */
    @Test
    void everyOtherSettingHasAUsableDefaultAndTheFixtureIsOff() {
        runner.run(context -> {
            OverpassProperties properties = context.getBean(OverpassProperties.class);
            assertThat(properties.baseUrl()).startsWith("https://");
            assertThat(properties.timeoutSeconds()).isPositive();
            assertThat(properties.maxResponseBytes()).isPositive();
            assertThat(properties.maxQueryChars()).isPositive();
            assertThat(properties.fixtureFile()).isNullOrEmpty();
        });
    }

    @Test
    void valuesCanBeOverriddenByConfiguration() {
        runner.withPropertyValues("overpass.base-url=http://localhost:12346/api/interpreter", "overpass.max-calls=2",
                "overpass.timeout-seconds=10", "overpass.max-response-bytes=1000", "overpass.max-query-chars=200",
                "overpass.fixture-file=dev/overpass-fixture.json").run(context -> {
                    OverpassProperties properties = context.getBean(OverpassProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("http://localhost:12346/api/interpreter");
                    assertThat(properties.maxCalls()).isEqualTo(2);
                    assertThat(properties.timeoutSeconds()).isEqualTo(10);
                    assertThat(properties.maxResponseBytes()).isEqualTo(1000);
                    assertThat(properties.maxQueryChars()).isEqualTo(200);
                    assertThat(properties.fixtureFile()).isEqualTo("dev/overpass-fixture.json");
                });
    }
}
