package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 007 T24, T25, FR-011, PO-4 (a), PO-10, research R6: the real service, reader and rule against a local fake
 * website whose page or image answers after 6 s. The 5 s budget is measured from the call of the service (the
 * reception of the request), queue wait included.
 */
class CardEnrichmentDeadlineTest {

    private static final Duration SLOW = Duration.ofSeconds(6);

    private FakeWebsite site;
    private ExecutorService executor;
    private CardEnrichmentService service;

    @BeforeEach
    void setUp() throws Exception {
        site = new FakeWebsite();
        executor = Executors.newFixedThreadPool(1); // PO-10: a stuck task would block the next enrichment
        CardEnrichmentProperties properties = TestCardEnrichmentProperties.withPortsAndExecutor(site.port(), 1);
        SafeWebFetcher fetcher = new SafeWebFetcher(properties, SafeWebFetcher::resolve,
                address -> address.isLoopbackAddress() || SafeWebFetcher.isPublic(address));
        service = new CardEnrichmentService(fetcher, new WebsitePageReader(properties), new LargestImageRule(),
                UnsplashStub.disabled(properties), properties, executor);
    }

    @AfterEach
    void tearDown() {
        site.close();
        executor.shutdownNow();
    }

    private WebsiteAddress website(String path) {
        return new WebsiteAddress(site.url(path));
    }

    /** T24: a website answering after 6 s gives no photo, returned before 6 s. */
    @Test
    void aSlowWebsiteGivesNoPhotoWithinTheBudget() {
        site.on("/", exchange -> {
            FakeWebsite.sleep(SLOW.toMillis());
            FakeWebsite.send(exchange, 200, "text/html", "<html></html>".getBytes(StandardCharsets.UTF_8));
        });

        long start = System.nanoTime();
        List<String> logs = CardEnrichmentServiceTest.logsOf(CardEnrichmentService.class, Level.INFO,
                () -> assertThat(service.enrich(website("/"), null)).isEqualTo(CardEnrichment.none()));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(4900)).isLessThan(SLOW);
        assertThat(logs).singleElement().asString().contains("TIMEOUT");
    }

    /**
     * T25: the website answers in 1 s with no sharing image; its largest image answers after 6 s. No photo before
     * 6 s; with one executor thread, the next enrichment starts at once (the slow image check is abandoned).
     */
    @Test
    void aSlowImageCheckGivesNoPhotoWithinTheBudget() {
        site.on("/", exchange -> {
            FakeWebsite.sleep(1000);
            FakeWebsite.send(exchange, 200, "text/html; charset=utf-8",
                    ("<html><body><img src=\"/salle.jpg\" width=\"1200\" height=\"800\">"
                            + "<img src=\"/plat.jpg\" width=\"300\" height=\"200\"></body></html>")
                            .getBytes(StandardCharsets.UTF_8));
        }).on("/salle.jpg", exchange -> {
            FakeWebsite.sleep(SLOW.toMillis());
            FakeWebsite.send(exchange, 200, "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8});
        }).image("/plat.jpg")
                .html("/next", "<html><head><meta property=\"og:image\" content=\"/partage.jpg\"></head></html>")
                .image("/partage.jpg");

        long start = System.nanoTime();
        List<String> logs = CardEnrichmentServiceTest.logsOf(CardEnrichmentService.class, Level.INFO,
                () -> assertThat(service.enrich(website("/"), null)).isEqualTo(CardEnrichment.none()));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(4900)).isLessThan(SLOW);
        assertThat(logs).singleElement().asString().contains("TIMEOUT");
        assertThat(site.paths()).containsExactly("/", "/salle.jpg");

        // The single executor thread is free again: the next enrichment is not queued behind the abandoned one.
        long next = System.nanoTime();
        CardEnrichment second = service.enrich(website("/next"), null);
        assertThat(second.photoOrigin()).isEqualTo(EnrichmentOutcome.SHARING_IMAGE);
        assertThat(Duration.ofNanos(System.nanoTime() - next)).isLessThan(Duration.ofSeconds(2));
    }

    /** FR-011, amendment (b): the Unsplash request counts in the 5 s; a slow Unsplash gives no photo in time. */
    @Test
    void aSlowUnsplashGivesNoPhotoWithinTheBudget() {
        CardEnrichmentProperties properties = UnsplashStub.pointedAt(site,
                TestCardEnrichmentProperties.withPortsAndExecutor(site.port(), 1), UnsplashStub.KEY);
        SafeWebFetcher fetcher = new SafeWebFetcher(properties, SafeWebFetcher::resolve,
                address -> address.isLoopbackAddress() || SafeWebFetcher.isPublic(address));
        CardEnrichmentService withUnsplash = new CardEnrichmentService(fetcher, new WebsitePageReader(properties),
                new LargestImageRule(), UnsplashStub.cuisinePhotos(properties, Clock.systemUTC()), properties,
                executor);
        site.html("/", "<html><body><p>Bienvenue</p></body></html>").on(UnsplashStub.SEARCH_PATH, exchange -> {
            FakeWebsite.sleep(SLOW.toMillis());
            FakeWebsite.send(exchange, 200, "application/json",
                    UnsplashStub.answerOf(15).getBytes(StandardCharsets.UTF_8));
        });

        long start = System.nanoTime();
        List<String> logs = CardEnrichmentServiceTest.logsOf(CardEnrichmentService.class, Level.INFO,
                () -> assertThat(withUnsplash.enrich(website("/"), "Pizzeria")).isEqualTo(CardEnrichment.none()));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(4900)).isLessThan(SLOW);
        assertThat(logs).singleElement().asString().contains("TIMEOUT");
    }
}
