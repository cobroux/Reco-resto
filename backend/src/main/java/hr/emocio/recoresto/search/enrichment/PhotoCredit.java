package hr.emocio.recoresto.search.enrichment;

/**
 * Credit of an Unsplash cuisine photo (spec 007 FR-015, contracts/card-enrichment.openapi.yaml {@code PhotoCredit}):
 * shown as "Photo : &lt;photographerName&gt; / Unsplash".
 *
 * @param photographerName the photographer name
 * @param photographerUrl the photographer profile on Unsplash, with the referral parameters
 * @param unsplashUrl the Unsplash home page, with the referral parameters
 */
public record PhotoCredit(String photographerName, String photographerUrl, String unsplashUrl) {
}
