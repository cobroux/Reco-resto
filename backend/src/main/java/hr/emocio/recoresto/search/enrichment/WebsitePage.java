package hr.emocio.recoresto.search.enrichment;

import java.net.URI;
import java.util.List;

/**
 * What the enrichment uses of a website page (data-model.md "Website page"); internal, never returned.
 *
 * @param finalUrl the address after redirects
 * @param sharingImage the declared sharing image, resolved (absolute http/https); null when none (PO-5)
 * @param candidates the page images, in page order (FR-004 step 1, PO-3)
 */
public record WebsitePage(URI finalUrl, String sharingImage, List<ImageCandidate> candidates) {

    public WebsitePage {
        candidates = List.copyOf(candidates);
    }
}
