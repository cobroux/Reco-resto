package hr.emocio.recoresto.search.enrichment;

/**
 * One usable result of an Unsplash search (spec 007 amendment (b), research R14): only absolute {@code https}
 * addresses and a named photographer.
 *
 * @param imageUrl {@code urls.regular}, hotlinked by the browser
 * @param photographerName {@code user.name}
 * @param photographerProfileUrl {@code user.links.html}, without the referral parameters
 */
public record UnsplashPhoto(String imageUrl, String photographerName, String photographerProfileUrl) {
}
