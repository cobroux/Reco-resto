package hr.emocio.recoresto.search.enrichment;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Answer of {@code GET /api/card-enrichment} (contracts/card-enrichment.openapi.yaml {@code CardEnrichment},
 * data-model.md "Card enrichment"). Both fields absent means "keep the tablecloth".
 *
 * @param photoUrl absolute address of an image found in the website page (FR-006)
 * @param photoOrigin {@code SHARING_IMAGE} or {@code LARGEST_PAGE_IMAGE}, present exactly when {@code photoUrl} is
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardEnrichment(String photoUrl, EnrichmentOutcome photoOrigin) {

    private static final CardEnrichment NONE = new CardEnrichment(null, null);

    public CardEnrichment {
        if ((photoUrl == null) != (photoOrigin == null) || (photoOrigin != null && !photoOrigin.hasPhoto())) {
            throw new IllegalArgumentException("A photo comes with its origin, and only then");
        }
    }

    public static CardEnrichment none() {
        return NONE;
    }

    /** A photo of the website (FR-003, FR-004). */
    public static CardEnrichment photo(String photoUrl, EnrichmentOutcome origin) {
        return new CardEnrichment(photoUrl, origin);
    }
}
