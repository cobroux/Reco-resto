package hr.emocio.recoresto.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Settings of spec 008 (data-model.md "Configuration", plan.md "Product owner decisions"). */
class GooglePlacesPropertiesTest {

    @EnableConfigurationProperties(GooglePlacesProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void defaultsAreTheDecidedValues() {
        runner.run(context -> {
            GooglePlacesProperties properties = context.getBean(GooglePlacesProperties.class);
            assertThat(properties.baseUrl()).isEqualTo("https://places.googleapis.com/v1");
            assertThat(properties.timeoutSeconds()).isEqualTo(10); // D1
            assertThat(properties.retryDelaysSeconds()).containsExactly(1); // D2
            assertThat(properties.maxResults()).isEqualTo(20); // D3
            assertThat(properties.photoMaxWidthPx()).isEqualTo(800); // D16
            assertThat(properties.languageCode()).isEqualTo("fr");
            assertThat(properties.regionCode()).isEqualTo("FR");
            assertThat(properties.fixtureFile()).isNullOrEmpty();
        });
    }

    /** The application starts without a key (FR-012: the search then answers "service unavailable"). */
    @Test
    void aBlankKeyIsAcceptedAtStartup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GooglePlacesProperties.class).apiKey()).isNullOrEmpty();
        });
    }

    @Test
    void everyValueCanBeOverriddenByItsEnvironmentVariable() {
        runner.withSystemProperties("GOOGLE_PLACES_BASE_URL=http://localhost:9999/v1",
                "GOOGLE_PLACES_API_KEY=k-123", "GOOGLE_PLACES_TIMEOUT_SECONDS=3",
                "GOOGLE_PLACES_RETRY_DELAYS_SECONDS=2,4", "GOOGLE_PLACES_MAX_RESULTS=5",
                "GOOGLE_PLACES_PHOTO_MAX_WIDTH_PX=400", "GOOGLE_PLACES_FIXTURE_FILE=dev/google-places-fixture.json")
                .run(context -> {
                    GooglePlacesProperties properties = context.getBean(GooglePlacesProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("http://localhost:9999/v1");
                    assertThat(properties.apiKey()).isEqualTo("k-123");
                    assertThat(properties.timeoutSeconds()).isEqualTo(3);
                    assertThat(properties.retryDelaysSeconds()).containsExactly(2, 4);
                    assertThat(properties.maxResults()).isEqualTo(5);
                    assertThat(properties.photoMaxWidthPx()).isEqualTo(400);
                    assertThat(properties.fixtureFile()).isEqualTo("dev/google-places-fixture.json");
                });
    }

    @Test
    void emptyRetryDelaysMeanASingleAttempt() {
        runner.withPropertyValues("google-places.retry-delays-seconds=").run(context -> assertThat(
                context.getBean(GooglePlacesProperties.class).retryDelaysSeconds()).isEmpty());
    }

    /** FR-013: the key never reaches a log line through the settings. */
    @Test
    void toStringNeverContainsTheKey() {
        runner.withPropertyValues("google-places.api-key=very-secret-key").run(context -> {
            GooglePlacesProperties properties = context.getBean(GooglePlacesProperties.class);
            assertThat(properties.apiKey()).isEqualTo("very-secret-key");
            assertThat(properties.toString()).doesNotContain("very-secret-key");
        });
    }
}
