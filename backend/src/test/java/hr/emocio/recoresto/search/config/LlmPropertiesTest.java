package hr.emocio.recoresto.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LlmPropertiesTest {

    @EnableConfigurationProperties(LlmProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    /** The proxy cuts the answer at 2000 tokens by default (seen 2026-11-25); the model accepts up to 10000. */
    @Test
    void theAnswerOfTheModelMayHaveTenThousandTokensByDefault() {
        runner.run(context -> assertThat(context.getBean(LlmProperties.class).maxTokens()).isEqualTo(10000));
    }

    @Test
    void theLimitCanBeOverriddenByConfiguration() {
        runner.withPropertyValues("llm.max-tokens=4000")
                .run(context -> assertThat(context.getBean(LlmProperties.class).maxTokens()).isEqualTo(4000));
    }
}
