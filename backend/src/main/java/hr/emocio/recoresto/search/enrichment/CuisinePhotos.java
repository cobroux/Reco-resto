package hr.emocio.recoresto.search.enrichment;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * The Unsplash photo of a restaurant cuisine, with its credit (spec 007 amendment (b), FR-013, FR-015, FR-016; plan
 * "Amendment (b) (Unsplash)"; data-model.md "Cuisine photo cache", "Photo credit").
 * <ul>
 * <li>Query: the cuisine shown on the card, trimmed, inner blanks collapsed; "restaurant" when unknown (T38).</li>
 * <li>Cache: the answer of one query (lower case) is reused while younger than {@code cache-ttl} (1 h, T42, T43),
 * in memory only; one request per query at a time, the others wait for it within their own budget; a failure is not
 * kept.</li>
 * <li>Photo: the usable result at {@code floorMod(website.hashCode(), count)} (T37): {@code String.hashCode} is fixed
 * by the Java language, so a website always gets the same photo.</li>
 * <li>Credit: the referral parameters {@code utm_source=<app-name>&utm_medium=referral} on the photographer profile and
 * on the Unsplash home page (FR-015).</li>
 * </ul>
 */
@Component
public class CuisinePhotos {

    /** Spec FR-013: the query when the cuisine is unknown. */
    static final String UNKNOWN_CUISINE_QUERY = "restaurant";

    /** Spec FR-015: "Unsplash" links to unsplash.com. */
    static final String UNSPLASH_HOME = "https://unsplash.com/";

    /** The answer to one query, possibly still in flight, and when its request was sent. */
    private record Answer(CompletableFuture<List<UnsplashPhoto>> photos, Instant sentAt) {
    }

    private final UnsplashClient client;
    private final Clock clock;
    private final Duration ttl;
    private final String referral;
    private final ConcurrentHashMap<String, Answer> cache = new ConcurrentHashMap<>();

    public CuisinePhotos(UnsplashClient client, CardEnrichmentProperties properties, Clock clock) {
        this.client = client;
        this.clock = clock;
        this.ttl = properties.unsplash().cacheTtl();
        this.referral = "utm_source=" + URLEncoder.encode(String.valueOf(properties.unsplash().appName()),
                StandardCharsets.UTF_8) + "&utm_medium=referral";
    }

    /** Whether an Unsplash key is configured; without it no request is ever sent (T36). */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /**
     * The cuisine photo of this restaurant, empty when Unsplash has no usable photo (T33).
     *
     * @throws FetchException {@code UNSPLASH_UNAVAILABLE} (T34) or {@code TIMEOUT} (FR-011)
     */
    public Optional<CardEnrichment> photoFor(String cuisine, WebsiteAddress website, Deadline deadline)
            throws FetchException {
        List<UnsplashPhoto> photos = photos(query(cuisine), deadline);
        if (photos.isEmpty()) {
            return Optional.empty();
        }
        UnsplashPhoto photo = photos.get(Math.floorMod(website.uri().toString().hashCode(), photos.size()));
        return Optional.of(CardEnrichment.cuisinePhoto(photo.imageUrl(), new PhotoCredit(photo.photographerName(),
                withReferral(photo.photographerProfileUrl()), withReferral(UNSPLASH_HOME))));
    }

    /** The Unsplash query of a cuisine (T38). */
    static String query(String cuisine) {
        if (cuisine == null || cuisine.isBlank()) {
            return UNKNOWN_CUISINE_QUERY;
        }
        return cuisine.trim().replaceAll("\\s+", " ");
    }

    private List<UnsplashPhoto> photos(String query, Deadline deadline) throws FetchException {
        String key = query.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        CompletableFuture<List<UnsplashPhoto>> mine = new CompletableFuture<>();
        Answer answer = cache.compute(key, (k, old) -> old != null && isFresh(old, now) ? old : new Answer(mine, now));
        if (answer.photos() == mine) {
            cache.values().removeIf(other -> other != answer && !isFresh(other, now));
            try {
                mine.complete(client.search(query, deadline));
            } catch (FetchException | RuntimeException e) {
                mine.completeExceptionally(e);
            } finally {
                if (mine.isCompletedExceptionally() || !mine.isDone()) {
                    cache.remove(key, answer); // a failure is not kept
                    mine.completeExceptionally(new IllegalStateException("Unsplash request abandoned"));
                }
            }
        }
        return await(answer.photos(), deadline);
    }

    private boolean isFresh(Answer answer, Instant now) {
        return now.isBefore(answer.sentAt().plus(ttl));
    }

    private static List<UnsplashPhoto> await(CompletableFuture<List<UnsplashPhoto>> photos, Deadline deadline)
            throws FetchException {
        try {
            return photos.get(Math.max(0, deadline.remaining().toNanos()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new FetchException(EnrichmentOutcome.TIMEOUT, "Budget spent waiting for Unsplash");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException(EnrichmentOutcome.TIMEOUT, "Interrupted waiting for Unsplash");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FetchException failure) {
                throw failure;
            }
            throw new FetchException(EnrichmentOutcome.UNSPLASH_UNAVAILABLE,
                    e.getCause() == null ? "Unsplash failed" : e.getCause().getClass().getSimpleName());
        }
    }

    /** The address with the referral parameters (FR-015); its fragment, if any, is dropped. */
    private String withReferral(String address) {
        int fragment = address.indexOf('#');
        String base = fragment < 0 ? address : address.substring(0, fragment);
        return base + (base.indexOf('?') < 0 ? "?" : "&") + referral;
    }
}
