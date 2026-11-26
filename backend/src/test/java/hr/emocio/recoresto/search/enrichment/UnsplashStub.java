package hr.emocio.recoresto.search.enrichment;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test helpers for amendment (b): Unsplash answers served by the local {@link FakeWebsite} (never the real Unsplash),
 * and the Unsplash classes built on it. Content is invented, NOT real data.
 */
final class UnsplashStub {

    static final String KEY = "unsplash-test-key-42";
    static final String SEARCH_PATH = "/search/photos";

    private UnsplashStub() {
    }

    /** One result as Unsplash sends it (only the fields read, plus one ignored field). */
    static String photo(int number) {
        return photo("https://images.unsplash.test/photo-" + number + "?w=1080", "Photographer " + number,
                "https://unsplash.com/@photographer" + number);
    }

    static String photo(String regular, String name, String profile) {
        return "{\"id\":\"x\",\"urls\":{\"raw\":\"https://images.unsplash.test/raw\",\"regular\":" + json(regular)
                + "},\"links\":{\"html\":\"https://unsplash.com/photos/x\"},\"user\":{\"name\":" + json(name)
                + ",\"links\":{\"html\":" + json(profile) + "}}}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** An answer with the given results. */
    static String answer(String... results) {
        return "{\"total\":" + results.length + ",\"total_pages\":1,\"results\":[" + String.join(",", results) + "]}";
    }

    /** An answer with photos 1 to n. */
    static String answerOf(int n) {
        return "{\"total\":" + n + ",\"total_pages\":1,\"results\":["
                + IntStream.rangeClosed(1, n).mapToObj(UnsplashStub::photo).collect(Collectors.joining(",")) + "]}";
    }

    /** The stub answers this JSON body to every search. */
    static void answers(FakeWebsite site, String body) {
        site.on(SEARCH_PATH, exchange -> FakeWebsite.send(exchange, 200, "application/json",
                body.getBytes(StandardCharsets.UTF_8)));
    }

    /** The given properties with Unsplash pointed at the stub, with the given key (blank: no key). */
    static CardEnrichmentProperties pointedAt(FakeWebsite site, CardEnrichmentProperties base, String key) {
        CardEnrichmentProperties.Unsplash u = base.unsplash();
        return new CardEnrichmentProperties(base.budgetSeconds(), base.maxPageBytes(), base.minImageSizePx(),
                base.maxRedirects(), base.allowedPorts(), base.userAgent(), base.executorSize(),
                new CardEnrichmentProperties.Unsplash("http://127.0.0.1:" + site.port(), key, u.appName(),
                        u.perPage(), u.cacheTtl()));
    }

    static UnsplashClient client(CardEnrichmentProperties properties) {
        return new UnsplashClient(RestClient.builder(), properties, JsonMapper.builder().build());
    }

    static CuisinePhotos cuisinePhotos(CardEnrichmentProperties properties, Clock clock) {
        return new CuisinePhotos(client(properties), properties, clock);
    }

    /** Unsplash disabled (no key): what the tests of the website steps use. */
    static CuisinePhotos disabled(CardEnrichmentProperties properties) {
        return cuisinePhotos(properties, Clock.systemUTC());
    }
}
