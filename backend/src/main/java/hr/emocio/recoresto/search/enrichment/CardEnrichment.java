package hr.emocio.recoresto.search.enrichment;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Answer of {@code GET /api/card-enrichment} (contracts/card-enrichment.openapi.yaml {@code CardEnrichment},
 * data-model.md "Card enrichment"). Both fields absent means "keep the tablecloth".
 *
 * @param photoUrl absolute address of an image found in the website page, or of the Unsplash cuisine photo (FR-006)
 * @param photoOrigin {@code SHARING_IMAGE}, {@code LARGEST_PAGE_IMAGE} or {@code UNSPLASH_CUISINE_PHOTO}, present
 *        exactly when {@code photoUrl} is
 * @param credit the Unsplash credit, present exactly when the origin is {@code UNSPLASH_CUISINE_PHOTO} (FR-015)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardEnrichment(String photoUrl, EnrichmentOutcome photoOrigin, PhotoCredit credit) {

    private static final CardEnrichment NONE = new CardEnrichment(null, null, null);

    public CardEnrichment {
        if ((photoUrl == null) != (photoOrigin == null) || (photoOrigin != null && !photoOrigin.hasPhoto())) {
            throw new IllegalArgumentException("A photo comes with its origin, and only then");
        }
        if ((credit != null) != (photoOrigin == EnrichmentOutcome.UNSPLASH_CUISINE_PHOTO)) {
            throw new IllegalArgumentException("An Unsplash photo comes with its credit, and only it");
        }
    }

    public static CardEnrichment none() {
        return NONE;
    }

    /** A photo of the website (FR-003, FR-004). */
    public static CardEnrichment photo(String photoUrl, EnrichmentOutcome origin) {
        return new CardEnrichment(photoUrl, origin, null);
    }

    /** An Unsplash cuisine photo with its credit (amendment (b), FR-013, FR-015). */
    public static CardEnrichment cuisinePhoto(String photoUrl, PhotoCredit credit) {
        return new CardEnrichment(photoUrl, EnrichmentOutcome.UNSPLASH_CUISINE_PHOTO, credit);
    }
}
