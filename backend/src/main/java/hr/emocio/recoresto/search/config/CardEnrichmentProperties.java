package hr.emocio.recoresto.search.config;

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
 */
@ConfigurationProperties("card-enrichment")
public record CardEnrichmentProperties(int budgetSeconds, int maxPageBytes, int minImageSizePx,
        int maxRedirects, List<Integer> allowedPorts, String userAgent, int executorSize) {

    public CardEnrichmentProperties {
        allowedPorts = allowedPorts == null ? List.of() : List.copyOf(allowedPorts);
    }
}
