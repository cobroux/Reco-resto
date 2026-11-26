package hr.emocio.recoresto.search.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Spec 007 PO-10 (2), research R6: the card enrichments run on {@code card-enrichment.executor-size} threads; the
     * others wait in the queue, and their wait counts in their budget.
     */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService cardEnrichmentExecutor(CardEnrichmentProperties properties) {
        return Executors.newFixedThreadPool(properties.executorSize(),
                Thread.ofPlatform().name("card-enrichment-", 1).daemon().factory());
    }
}
