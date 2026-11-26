package hr.emocio.recoresto.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ToolSearchPropertiesTest {

    @EnableConfigurationProperties(ToolSearchProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void theLimitsOfApplicationYmlAreRead() {
        runner.run(context -> assertThat(context.getBean(ToolSearchProperties.class))
                .isEqualTo(new ToolSearchProperties(300, 5000, 100, 120, 20, 3, 2)));
    }

    @Test
    void aLimitCanBeOverriddenByConfiguration() {
        runner.withPropertyValues("search.tool.max-candidates=30")
                .run(context -> assertThat(context.getBean(ToolSearchProperties.class).maxCandidates()).isEqualTo(30));
    }
}
