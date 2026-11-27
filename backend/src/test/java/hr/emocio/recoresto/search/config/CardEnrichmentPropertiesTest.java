package hr.emocio.recoresto.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Settings of spec 007 (data-model.md "Configuration", plan.md "Product owner decisions (2026-11-26)"). */
class CardEnrichmentPropertiesTest {

    @EnableConfigurationProperties(CardEnrichmentProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @Test
    void defaultsAreTheDecidedValues() {
        runner.run(context -> {
            CardEnrichmentProperties properties = context.getBean(CardEnrichmentProperties.class);
            assertThat(properties.budgetSeconds()).isEqualTo(5); // FR-011
            assertThat(properties.maxPageBytes()).isEqualTo(1_048_576); // PO-1
            assertThat(properties.minImageSizePx()).isEqualTo(100); // PO-3
            assertThat(properties.maxRedirects()).isEqualTo(5); // PO-7
            assertThat(properties.allowedPorts()).containsExactly(80, 443); // PO-8
            assertThat(properties.userAgent()).isEqualTo("RecoResto/0.1 (proof of concept)"); // PO-6, OQ-2
            assertThat(properties.executorSize()).isEqualTo(8); // PO-10 (2)
            // Amendment (b), plan "Amendment (b) (Unsplash)" table "Configuration"
            CardEnrichmentProperties.Unsplash unsplash = properties.unsplash();
            assertThat(unsplash.baseUrl()).isEqualTo("https://api.unsplash.com"); // research R14
            assertThat(unsplash.accessKey()).isEmpty(); // no default key (FR-014)
            assertThat(unsplash.hasAccessKey()).isFalse(); // T36
            assertThat(unsplash.appName()).isEqualTo("reco_resto"); // FR-015
            assertThat(unsplash.perPage()).isEqualTo(15); // FR-013
            assertThat(unsplash.cacheTtl()).isEqualTo(Duration.ofHours(1)); // FR-016
        });
    }

    @Test
    void everyValueCanBeOverriddenByItsEnvironmentVariable() {
        runner.withSystemProperties("CARD_ENRICHMENT_BUDGET_SECONDS=3", "CARD_ENRICHMENT_MAX_PAGE_BYTES=2048",
                "CARD_ENRICHMENT_MIN_IMAGE_SIZE_PX=50",
                "CARD_ENRICHMENT_MAX_REDIRECTS=2", "CARD_ENRICHMENT_ALLOWED_PORTS=443",
                "CARD_ENRICHMENT_USER_AGENT=Other/1.0", "CARD_ENRICHMENT_EXECUTOR_SIZE=2",
                "CARD_ENRICHMENT_UNSPLASH_BASE_URL=http://stub.test", "UNSPLASH_ACCESS_KEY=secret-key-1",
                "CARD_ENRICHMENT_UNSPLASH_APP_NAME=other_app", "CARD_ENRICHMENT_UNSPLASH_PER_PAGE=10",
                "CARD_ENRICHMENT_UNSPLASH_CACHE_TTL=30m")
                .run(context -> {
                    CardEnrichmentProperties properties = context.getBean(CardEnrichmentProperties.class);
                    assertThat(properties.budgetSeconds()).isEqualTo(3);
                    assertThat(properties.maxPageBytes()).isEqualTo(2048);
                    assertThat(properties.minImageSizePx()).isEqualTo(50);
                    assertThat(properties.maxRedirects()).isEqualTo(2);
                    assertThat(properties.allowedPorts()).containsExactly(443);
                    assertThat(properties.userAgent()).isEqualTo("Other/1.0");
                    assertThat(properties.executorSize()).isEqualTo(2);
                    CardEnrichmentProperties.Unsplash unsplash = properties.unsplash();
                    assertThat(unsplash.baseUrl()).isEqualTo("http://stub.test");
                    assertThat(unsplash.accessKey()).isEqualTo("secret-key-1");
                    assertThat(unsplash.hasAccessKey()).isTrue();
                    assertThat(unsplash.appName()).isEqualTo("other_app");
                    assertThat(unsplash.perPage()).isEqualTo(10);
                    assertThat(unsplash.cacheTtl()).isEqualTo(Duration.ofMinutes(30));
                });
    }

    /** FR-014, T39: the Unsplash key never appears when the settings are printed. */
    @Test
    void theUnsplashKeyIsNeverPrinted() {
        runner.withSystemProperties("UNSPLASH_ACCESS_KEY=secret-key-1").run(context -> {
            CardEnrichmentProperties properties = context.getBean(CardEnrichmentProperties.class);
            assertThat(properties.toString()).doesNotContain("secret-key-1").contains("***");
            assertThat(properties.unsplash().toString()).doesNotContain("secret-key-1");
        });
    }
}
