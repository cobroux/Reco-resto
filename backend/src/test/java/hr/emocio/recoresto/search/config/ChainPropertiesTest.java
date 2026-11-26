package hr.emocio.recoresto.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ChainPropertiesTest {

    @EnableConfigurationProperties(ChainProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void defaultsHoldTheProposedChainsAndTheGenericHosts() {
        runner.run(context -> {
            ChainProperties properties = context.getBean(ChainProperties.class);
            assertThat(properties.knownNames()).hasSize(37).doesNotContain("Prêt à Manger").contains("McDonald's", "O'Tacos", "Big Mamma",
                    "Brioche Dorée");
            assertThat(properties.genericHosts()).contains("facebook.com", "instagram.com", "linktr.ee",
                    "google.com", "tripadvisor.*", "thefork.*", "lafourchette.*", "ubereats.com", "deliveroo.*");
            assertThat(properties.genericHosts()).doesNotContain("wixsite.com", "wixstudio.com");
        });
    }

    @Test
    void valuesCanBeOverriddenByConfiguration() {
        runner.withPropertyValues("search.chains.known-names=Foo,Bar", "search.chains.generic-hosts=example.com")
                .run(context -> {
                    ChainProperties properties = context.getBean(ChainProperties.class);
                    assertThat(properties.knownNames()).containsExactly("Foo", "Bar");
                    assertThat(properties.genericHosts()).containsExactly("example.com");
                });
    }
}
