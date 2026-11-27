package hr.emocio.recoresto.search.enrichment;

import java.net.URI;

/**
 * A website page as read by {@link SafeWebFetcher} (data-model.md "Website page"): never returned to the browser.
 *
 * @param finalUrl the address after redirects (T30), base of the relative addresses of the page
 * @param body the start of the page, cut at {@code card-enrichment.max-page-bytes} (PO-1)
 * @param charset the charset of the {@code Content-Type} header, null when not given (the parser then detects it)
 */
public record FetchedPage(URI finalUrl, byte[] body, String charset) {
}
