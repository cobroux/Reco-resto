package hr.emocio.recoresto.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SearchPropertiesTest {

    @EnableConfigurationProperties(SearchProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void defaultsAreRadius2Point5KmAndDeckSize15() {
        runner.run(context -> {
            SearchProperties properties = context.getBean(SearchProperties.class);
            assertThat(properties.defaultRadiusKm()).isEqualTo(2.5);
            assertThat(properties.deckSize()).isEqualTo(15);
        });
    }

    @Test
    void valuesCanBeOverriddenByConfiguration() {
        runner.withPropertyValues("search.default-radius-km=5", "search.deck-size=10").run(context -> {
            SearchProperties properties = context.getBean(SearchProperties.class);
            assertThat(properties.defaultRadiusKm()).isEqualTo(5.0);
            assertThat(properties.deckSize()).isEqualTo(10);
        });
    }
}
