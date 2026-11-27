package hr.emocio.recoresto.search.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Card website enrichment settings (spec 007, data-model.md "Configuration"). The values live in
 * {@code application.yml}, each with the decision it comes from.
 *
 * @param budgetSeconds whole time given to one enrichment, from the reception of the request (FR-011, PO-4 a)
 * @param maxPageBytes largest part of a website page read; a longer page is cut and parsed (PO-1)
 * @param minImageSizePx images declared narrower or lower than this are not candidates (PO-3)
 * @param maxRedirects largest number of redirects followed (PO-7)
 * @param allowedPorts the only ports the backend connects to (PO-8)
 * @param userAgent {@code User-Agent} header of every request to a website (PO-6)
 * @param executorSize how many enrichments run at the same time (PO-10 (2))
 * @param unsplash the Unsplash cuisine photo of amendment (b) (FR-013 to FR-016); an absent section means no key
 */
@ConfigurationProperties("card-enrichment")
public record CardEnrichmentProperties(int budgetSeconds, int maxPageBytes, int minImageSizePx,
        int maxRedirects, List<Integer> allowedPorts, String userAgent, int executorSize, Unsplash unsplash) {

    public CardEnrichmentProperties {
        allowedPorts = allowedPorts == null ? List.of() : List.copyOf(allowedPorts);
        unsplash = unsplash == null ? new Unsplash(null, null, null, 0, null) : unsplash;
    }

    /**
     * Unsplash API settings (spec 007 amendment (b), plan "Amendment (b) (Unsplash)", research R14).
     *
     * @param baseUrl base address of the Unsplash API
     * @param accessKey application access key, sent in the {@code Authorization} header only; blank means no Unsplash
     *        step (T36); never logged, see {@link #toString()} (FR-014)
     * @param appName application name of the {@code utm_source} referral parameter of the credit links (FR-015)
     * @param perPage results asked for, the photo is chosen among them (FR-013)
     * @param cacheTtl how long the answer of one query is reused (FR-016)
     */
    public record Unsplash(String baseUrl, String accessKey, String appName, int perPage, Duration cacheTtl) {

        /** Whether a key is configured. */
        public boolean hasAccessKey() {
            return accessKey != null && !accessKey.isBlank();
        }

        /** Never shows the key (FR-014). */
        @Override
        public String toString() {
            return "Unsplash[baseUrl=" + baseUrl + ", accessKey=" + (hasAccessKey() ? "***" : "<missing>")
                    + ", appName=" + appName + ", perPage=" + perPage + ", cacheTtl=" + cacheTtl + "]";
        }
    }
}
