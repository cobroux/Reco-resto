package hr.emocio.recoresto.search.enrichment;

/**
 * How one enrichment ended (spec 007 data-model.md "Enrichment outcome", research R12). Logged once per request; only
 * the photo origin reaches the response.
 */
public enum EnrichmentOutcome {
    /** The declared sharing image answers as an image (FR-003). */
    SHARING_IMAGE,
    /** The largest page image that answers as an image (FR-004, amendment (a)). */
    LARGEST_PAGE_IMAGE,
    /** No usable sharing image and no page image left by the rule: no image is checked (T10, T11). */
    NO_IMAGE,
    /** No usable sharing image and none of the checked page images answers as an image (T28). */
    NO_DISPLAYABLE_IMAGE,
    /** Connection refused, unknown host, error status, too many redirects (T26). */
    UNREACHABLE,
    /** An address refused by the SSRF rule: scheme, user-info, port or a non-public address (research R5). */
    BLOCKED_ADDRESS,
    /** The website answers something other than an HTML page. */
    NOT_HTML,
    /** The 5 s budget is spent (FR-011, T24, T25). */
    TIMEOUT,
    /** Amendment (b): an Unsplash photo of the restaurant cuisine, with its credit (FR-013, T31). */
    UNSPLASH_CUISINE_PHOTO,
    /** Amendment (b): Unsplash answered no usable photo (T33). */
    NO_CUISINE_PHOTO,
    /** Amendment (b): Unsplash refused or failed: 429, other status, unreachable, unreadable answer (T34). */
    UNSPLASH_UNAVAILABLE;

    /** Whether the outcome gives a photo. */
    public boolean hasPhoto() {
        return this == SHARING_IMAGE || this == LARGEST_PAGE_IMAGE || this == UNSPLASH_CUISINE_PHOTO;
    }
}
