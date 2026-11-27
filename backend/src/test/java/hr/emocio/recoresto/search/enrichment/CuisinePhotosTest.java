package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 007 amendment (b): T33, T37, T38, T42, T43, FR-013 (position), FR-015 (credit), FR-016 (1 h cache); plan.md
 * "Amendment (b) (Unsplash)"; data-model.md "Cuisine photo cache", "Photo credit". Local Unsplash stub.
 */
class CuisinePhotosTest {

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-26T12:00:00Z"));

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private FakeWebsite site;
    private MovableClock clock;
    private CuisinePhotos photos;

    @BeforeEach
    void setUp() throws Exception {
        site = new FakeWebsite();
        clock = new MovableClock();
        CardEnrichmentProperties properties = UnsplashStub.pointedAt(site, TestCardEnrichmentProperties.defaults(),
                UnsplashStub.KEY);
        photos = UnsplashStub.cuisinePhotos(properties, clock);
        UnsplashStub.answers(site, UnsplashStub.answerOf(15));
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    private static WebsiteAddress website(String address) {
        return new WebsiteAddress(URI.create(address));
    }

    private static Deadline budget() {
        return Deadline.after(Duration.ofSeconds(5));
    }

    private CardEnrichment photoFor(String cuisine, String address) throws FetchException {
        return photos.photoFor(cuisine, website(address), budget()).orElseThrow();
    }

    /** The photo number (1-15) the stub gives for this position rule. */
    private static int expectedNumber(String address, int count) {
        return Math.floorMod(address.hashCode(), count) + 1;
    }

    private List<String> queries() {
        return site.requests().stream().map(FakeWebsite.Request::query).toList();
    }

    /** T31, FR-015: the photo, its origin and its credit with the referral parameters. */
    @Test
    void thePhotoComesWithItsCredit() throws Exception {
        String address = "https://www.trattoria.test/";
        int n = expectedNumber(address, 15);

        CardEnrichment enrichment = photoFor("Restaurant italien", address);

        assertThat(enrichment.photoUrl()).isEqualTo("https://images.unsplash.test/photo-" + n + "?w=1080");
        assertThat(enrichment.photoOrigin()).isEqualTo(EnrichmentOutcome.UNSPLASH_CUISINE_PHOTO);
        assertThat(enrichment.credit()).isEqualTo(new PhotoCredit("Photographer " + n,
                "https://unsplash.com/@photographer" + n + "?utm_source=reco_resto&utm_medium=referral",
                "https://unsplash.com/?utm_source=reco_resto&utm_medium=referral"));
        assertThat(queries()).containsExactly("query=Restaurant italien&per_page=15&lang=fr");
    }

    /** FR-015: a profile address that already has a query keeps it; its fragment is dropped. */
    @Test
    void theReferralParametersAreAddedToAnExistingQuery() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answer(UnsplashStub.photo("https://images.unsplash.test/a",
                "Jane Doe", "https://unsplash.com/@jane?lang=fr#top")));

        assertThat(photoFor("Pizzeria", "https://a.test/").credit().photographerUrl())
                .isEqualTo("https://unsplash.com/@jane?lang=fr&utm_source=reco_resto&utm_medium=referral");
    }

    /** T37: same cuisine, same 15 photos: each website its own hash position, always the same one. */
    @Test
    void eachWebsiteGetsThePhotoAtItsHashPosition() throws Exception {
        String first = "https://www.trattoria-a.test/";
        String second = "https://www.trattoria-b.test/";
        assertThat(expectedNumber(first, 15)).isNotEqualTo(expectedNumber(second, 15));

        String a = photoFor("Restaurant italien", first).photoUrl();
        String b = photoFor("Restaurant italien", second).photoUrl();

        assertThat(a).isEqualTo("https://images.unsplash.test/photo-" + expectedNumber(first, 15) + "?w=1080");
        assertThat(b).isEqualTo("https://images.unsplash.test/photo-" + expectedNumber(second, 15) + "?w=1080");
        assertThat(photoFor("Restaurant italien", first).photoUrl()).isEqualTo(a);
    }

    /** FR-013: the position is taken among the usable results, never out of range. */
    @Test
    void thePositionIsTakenAmongTheResults() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answerOf(4));
        String address = "https://www.creperie.test/";

        assertThat(photoFor("Crêperie", address).photoUrl())
                .isEqualTo("https://images.unsplash.test/photo-" + expectedNumber(address, 4) + "?w=1080");
    }

    /** T38: an unknown cuisine searches "restaurant". */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void anUnknownCuisineSearchesRestaurant(String cuisine) throws Exception {
        photoFor(cuisine, "https://a.test/");

        assertThat(queries()).containsExactly("query=restaurant&per_page=15&lang=fr");
    }

    @Test
    void theCuisineIsTrimmedAndItsInnerBlanksCollapsed() {
        assertThat(CuisinePhotos.query("  Restaurant \t  italien ")).isEqualTo("Restaurant italien");
        assertThat(CuisinePhotos.query(null)).isEqualTo("restaurant");
    }

    /** T33: Unsplash answers no photo. */
    @Test
    void noResultGivesNoPhoto() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answer());

        assertThat(photos.photoFor("Pizzeria", website("https://a.test/"), budget())).isEmpty();
    }

    /** T42: two restaurants of the same cuisine 10 minutes apart cost one request. */
    @Test
    void theAnswerIsReusedWithinTheHour() throws Exception {
        photoFor("Restaurant italien", "https://a.test/");
        clock.advance(Duration.ofMinutes(10));
        photoFor("Restaurant italien", "https://b.test/");

        assertThat(site.requests()).hasSize(1);
    }

    /** FR-016: the cache key ignores case and blanks; another cuisine is another request. */
    @Test
    void theCacheIsKeyedByTheNormalizedQuery() throws Exception {
        photoFor("Restaurant italien", "https://a.test/");
        photoFor(" restaurant   ITALIEN ", "https://b.test/");
        photoFor("Pizzeria", "https://c.test/");

        assertThat(queries()).containsExactly("query=Restaurant italien&per_page=15&lang=fr",
                "query=Pizzeria&per_page=15&lang=fr");
    }

    /** T43: 61 minutes apart, two requests. */
    @Test
    void theAnswerExpiresAfterOneHour() throws Exception {
        photoFor("Restaurant italien", "https://a.test/");
        clock.advance(Duration.ofMinutes(61));
        photoFor("Restaurant italien", "https://b.test/");

        assertThat(site.requests()).hasSize(2);
    }

    /** An empty answer is an answer: it is reused too. */
    @Test
    void anEmptyAnswerIsReused() throws Exception {
        UnsplashStub.answers(site, UnsplashStub.answer());

        photos.photoFor("Pizzeria", website("https://a.test/"), budget());
        photos.photoFor("Pizzeria", website("https://b.test/"), budget());

        assertThat(site.requests()).hasSize(1);
    }

    /** A failure is not kept: the next card asks again (T34). */
    @Test
    void aFailureIsNotCached() throws Exception {
        site.on(UnsplashStub.SEARCH_PATH, exchange -> FakeWebsite.send(exchange, 429, "text/plain",
                "Rate Limit Exceeded".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> photos.photoFor("Pizzeria", website("https://a.test/"), budget()))
                .isInstanceOfSatisfying(FetchException.class,
                        e -> assertThat(e.outcome()).isEqualTo(EnrichmentOutcome.UNSPLASH_UNAVAILABLE));

        UnsplashStub.answers(site, UnsplashStub.answerOf(15));
        assertThat(photos.photoFor("Pizzeria", website("https://a.test/"), budget())).isPresent();
        assertThat(site.requests()).hasSize(2);
    }

    /** FR-016: enrichments of one cuisine at the same time share the request in flight. */
    @Test
    void concurrentEnrichmentsOfOneCuisineShareOneRequest() throws Exception {
        site.on(UnsplashStub.SEARCH_PATH, exchange -> {
            FakeWebsite.sleep(500);
            FakeWebsite.send(exchange, 200, "application/json",
                    UnsplashStub.answerOf(15).getBytes(StandardCharsets.UTF_8));
        });
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Optional<CardEnrichment>>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String address = "https://site-" + i + ".test/";
                Callable<Optional<CardEnrichment>> call = () -> photos.photoFor("Pizzeria", website(address),
                        budget());
                results.add(pool.submit(call));
            }
            for (Future<Optional<CardEnrichment>> result : results) {
                assertThat(result.get()).isPresent();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(site.requests()).hasSize(1);
    }

    /** T36: no key, not enabled. */
    @Test
    void withoutKeyItIsNotEnabled() {
        assertThat(photos.isEnabled()).isTrue();
        assertThat(UnsplashStub.disabled(TestCardEnrichmentProperties.defaults()).isEnabled()).isFalse();
    }
}
