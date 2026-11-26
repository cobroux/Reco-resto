package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 007 amendment (b): T31 (request), T34 (failures), T36 (no key), T39 (key only in the header, never logged),
 * FR-011 (remaining budget as timeout); research R14. The local stub stands for Unsplash.
 */
class UnsplashClientTest {

    private FakeWebsite site;
    private UnsplashClient client;

    @BeforeEach
    void setUp() throws Exception {
        site = new FakeWebsite();
        client = UnsplashStub.client(
                UnsplashStub.pointedAt(site, TestCardEnrichmentProperties.defaults(), UnsplashStub.KEY));
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    private static Deadline budget() {
        return Deadline.after(Duration.ofSeconds(5));
    }

    /** T31, T39: query, 15 results, French, key in the Authorization header only, API version 1. */
    @Test
    void theSearchSendsTheQueryInFrenchWithTheKeyInTheHeaderOnly() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answerOf(2));

        List<UnsplashPhoto> photos = client.search("Restaurant italien", budget());

        assertThat(photos).hasSize(2);
        FakeWebsite.Request request = site.requests().getFirst();
        assertThat(request.path()).isEqualTo("/search/photos");
        assertThat(request.query()).isEqualTo("query=Restaurant italien&per_page=15&lang=fr")
                .doesNotContain(UnsplashStub.KEY);
        assertThat(request.headers().getFirst("Authorization")).isEqualTo("Client-ID " + UnsplashStub.KEY);
        assertThat(request.headers().getFirst("Accept-Version")).isEqualTo("v1");
    }

    @Test
    void theAnswerIsReadIntoPhotosInAnswerOrder() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answerOf(3));

        List<UnsplashPhoto> photos = client.search("Pizzeria", budget());

        assertThat(photos).containsExactly(
                new UnsplashPhoto("https://images.unsplash.test/photo-1?w=1080", "Photographer 1",
                        "https://unsplash.com/@photographer1"),
                new UnsplashPhoto("https://images.unsplash.test/photo-2?w=1080", "Photographer 2",
                        "https://unsplash.com/@photographer2"),
                new UnsplashPhoto("https://images.unsplash.test/photo-3?w=1080", "Photographer 3",
                        "https://unsplash.com/@photographer3"));
    }

    /** Research R14: only absolute https addresses and a named photographer reach the browser. */
    @Test
    void unusableResultsAreSkipped() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answer(
                UnsplashStub.photo("http://images.unsplash.test/plain-http", "A", "https://unsplash.com/@a"),
                UnsplashStub.photo("javascript:alert(1)", "B", "https://unsplash.com/@b"),
                UnsplashStub.photo("https://images.unsplash.test/ok", "  ", "https://unsplash.com/@c"),
                UnsplashStub.photo("https://images.unsplash.test/ok", "D", "javascript:alert(1)"),
                UnsplashStub.photo(null, "E", "https://unsplash.com/@e"),
                UnsplashStub.photo("https://images.unsplash.test/ok", null, "https://unsplash.com/@f"),
                UnsplashStub.photo("https://images.unsplash.test/bad path", "G", "https://unsplash.com/@g"),
                UnsplashStub.photo("https://images.unsplash.test/good", "Jane Doe", "https://unsplash.com/@jane")));

        assertThat(client.search("Pizzeria", budget())).containsExactly(new UnsplashPhoto(
                "https://images.unsplash.test/good", "Jane Doe", "https://unsplash.com/@jane"));
    }

    /** FR-013: only the first 15 results count, even if more are answered. */
    @Test
    void atMostTheFirstFifteenResultsAreKept() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answerOf(20));

        List<UnsplashPhoto> photos = client.search("Pizzeria", budget());

        assertThat(photos).hasSize(15);
        assertThat(photos.getLast().photographerName()).isEqualTo("Photographer 15");
    }

    @Test
    void noResultIsAnEmptyList() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answer());

        assertThat(client.search("Pizzeria", budget())).isEmpty();
    }

    /** T34, T39: limit reached, server error, unreadable answer; WARN with the status, never the key. */
    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503, 401, 403})
    void anErrorStatusIsUnavailableAndLoggedWithoutTheKey(int status) {
        site.on(UnsplashStub.SEARCH_PATH, exchange -> FakeWebsite.send(exchange, status, "text/plain",
                ("Rate Limit Exceeded " + UnsplashStub.KEY).getBytes(StandardCharsets.UTF_8)));

        List<String> warnings = CardEnrichmentServiceTest.logsOf(UnsplashClient.class, Level.WARN,
                () -> assertThatThrownBy(() -> client.search("Pizzeria", budget()))
                        .isInstanceOfSatisfying(FetchException.class, e -> assertThat(e.outcome())
                                .isEqualTo(EnrichmentOutcome.UNSPLASH_UNAVAILABLE)));

        assertThat(warnings).singleElement().asString().contains("HTTP " + status)
                .doesNotContain(UnsplashStub.KEY).doesNotContain("Pizzeria");
    }

    @Test
    void anUnreadableAnswerIsUnavailable() {
        site.on(UnsplashStub.SEARCH_PATH, exchange -> FakeWebsite.send(exchange, 200, "application/json",
                "not json".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client.search("Pizzeria", budget())).isInstanceOfSatisfying(FetchException.class,
                e -> assertThat(e.outcome()).isEqualTo(EnrichmentOutcome.UNSPLASH_UNAVAILABLE));
    }

    /** T34: Unsplash unreachable (connection refused). */
    @Test
    void anUnreachableUnsplashIsUnavailableAndLoggedWithoutTheKey() {
        site.close();

        List<String> warnings = CardEnrichmentServiceTest.logsOf(UnsplashClient.class, Level.WARN,
                () -> assertThatThrownBy(() -> client.search("Pizzeria", budget()))
                        .isInstanceOfSatisfying(FetchException.class, e -> assertThat(e.outcome())
                                .isEqualTo(EnrichmentOutcome.UNSPLASH_UNAVAILABLE)));

        assertThat(warnings).singleElement().asString().contains("Unsplash").doesNotContain(UnsplashStub.KEY);
    }

    /** FR-011: the request gets the remaining budget as timeout. */
    @Test
    void aSlowUnsplashIsAbandonedAtTheEndOfTheBudget() {
        site.on(UnsplashStub.SEARCH_PATH, exchange -> {
            FakeWebsite.sleep(3000);
            FakeWebsite.send(exchange, 200, "application/json",
                    UnsplashStub.answerOf(1).getBytes(StandardCharsets.UTF_8));
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.search("Pizzeria", Deadline.after(Duration.ofMillis(500))))
                .isInstanceOfSatisfying(FetchException.class,
                        e -> assertThat(e.outcome()).isEqualTo(EnrichmentOutcome.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void aSpentBudgetSendsNoRequest() {
        assertThatThrownBy(() -> client.search("Pizzeria", Deadline.after(Duration.ZERO)))
                .isInstanceOfSatisfying(FetchException.class,
                        e -> assertThat(e.outcome()).isEqualTo(EnrichmentOutcome.TIMEOUT));
        assertThat(site.requests()).isEmpty();
    }

    /** T36: without a key the client is not enabled and sends nothing. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void withoutKeyTheClientIsNotEnabled(String key) {
        CardEnrichmentProperties properties = UnsplashStub.pointedAt(site, TestCardEnrichmentProperties.defaults(),
                key);
        UnsplashClient noKey = UnsplashStub.client(properties);

        assertThat(noKey.isEnabled()).isFalse();
        assertThatThrownBy(() -> noKey.search("Pizzeria", budget())).isInstanceOfSatisfying(FetchException.class,
                e -> assertThat(e.outcome()).isEqualTo(EnrichmentOutcome.UNSPLASH_UNAVAILABLE));
        assertThat(site.requests()).isEmpty();
        assertThat(client.isEnabled()).isTrue();
    }
}
