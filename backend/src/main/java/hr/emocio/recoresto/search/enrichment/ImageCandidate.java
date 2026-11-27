package hr.emocio.recoresto.search.enrichment;

/**
 * One image of a website page that the largest image rule may use (data-model.md "Image candidate", spec 007 FR-004).
 *
 * @param url absolute http or https address
 * @param alt the {@code alt} attribute, trimmed; null when absent or blank
 * @param width the declared {@code width} attribute when numeric
 * @param height the declared {@code height} attribute when numeric
 * @param srcsetWidth the width descriptor ({@code 1200w}) of the {@code srcset} entry the address comes from; null
 *        when the address does not come from a {@code srcset} entry with a width descriptor
 */
public record ImageCandidate(String url, String alt, Integer width, Integer height, Integer srcsetWidth) {
}
