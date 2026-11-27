package hr.emocio.recoresto.search.enrichment;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Reads a website page with jsoup (spec 007 research R4, data-model.md "Website page"). Every address is resolved
 * against the final page address and the page {@code <base href>} (T6).
 * <ul>
 * <li>Sharing image (PO-5): the first non-blank {@code og:image}, else {@code og:image:secure_url}, else
 * {@code og:image:url}, else {@code twitter:image}, else {@code <link rel="image_src">}. Meta tags are read from their
 * {@code property} or {@code name} attribute.</li>
 * <li>Candidates (FR-004 step 1, PO-3, OQ-3): one per {@code <img>}: the largest {@code srcset} entry (with its width
 * descriptor), else {@code data-src}, else {@code data-lazy-src}, else {@code src}; dropped when not http/https
 * ({@code data:} included), {@code .svg} or {@code .ico}, or declared under {@code card-enrichment.min-image-size-px}
 * wide or high; deduplicated by address, in page order, all of them (the largest image may be anywhere in the
 * page; {@link LargestImageRule} applies the other steps).</li>
 * </ul>
 */
@Component
public class WebsitePageReader {

    private static final List<String> SHARING_META = List.of("og:image", "og:image:secure_url", "og:image:url",
            "twitter:image");
    private static final List<String> IMAGE_ATTRIBUTES = List.of("data-src", "data-lazy-src", "src");
    private static final Set<String> DROPPED_EXTENSIONS = Set.of(".svg", ".ico");

    private final CardEnrichmentProperties properties;

    public WebsitePageReader(CardEnrichmentProperties properties) {
        this.properties = properties;
    }

    public WebsitePage read(FetchedPage page) {
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(page.body()), page.charset(), page.finalUrl().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new WebsitePage(page.finalUrl(), sharingImage(document), candidates(document));
    }

    private static String sharingImage(Document document) {
        for (String key : SHARING_META) {
            for (Element meta : document.select("meta")) {
                if (key.equalsIgnoreCase(meta.attr("property")) || key.equalsIgnoreCase(meta.attr("name"))) {
                    String address = httpAddress(meta, meta.attr("content"));
                    if (address != null) {
                        return address;
                    }
                }
            }
        }
        for (Element link : document.select("link[rel]")) {
            if (link.attr("rel").toLowerCase(Locale.ROOT).matches("(.*\\s)?image_src(\\s.*)?")) {
                String address = httpAddress(link, link.attr("href"));
                if (address != null) {
                    return address;
                }
            }
        }
        return null;
    }

    private List<ImageCandidate> candidates(Document document) {
        Set<String> seen = new LinkedHashSet<>();
        List<ImageCandidate> candidates = new ArrayList<>();
        for (Element img : document.select("img")) {
            Srcset.Entry srcset = Srcset.largest(img.attr("srcset"));
            String address = httpAddress(img, srcset != null ? srcset.url() : fallbackAddress(img));
            Integer width = declaredSize(img.attr("width"));
            Integer height = declaredSize(img.attr("height"));
            if (address == null || hasDroppedExtension(address) || tooSmall(width) || tooSmall(height)
                    || !seen.add(address)) {
                continue;
            }
            String alt = img.attr("alt").isBlank() ? null : img.attr("alt").trim();
            candidates.add(new ImageCandidate(address, alt, width, height, srcset != null ? srcset.width() : null));
        }
        return candidates;
    }

    /** OQ-3, when there is no srcset entry: data-src, else data-lazy-src, else src; null when none is given. */
    private static String fallbackAddress(Element img) {
        for (String attribute : IMAGE_ATTRIBUTES) {
            if (!img.attr(attribute).isBlank()) {
                return img.attr(attribute);
            }
        }
        return null;
    }

    /** The value resolved against the base of the page, or null when it is not an http/https address. */
    private static String httpAddress(Element element, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String resolved = StringUtil.resolve(element.baseUri(), value.trim());
        String lower = resolved.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? resolved : null;
    }

    private static boolean hasDroppedExtension(String address) {
        String path = address.replaceFirst("[?#].*$", "").toLowerCase(Locale.ROOT);
        return DROPPED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private boolean tooSmall(Integer declared) {
        return declared != null && declared < properties.minImageSizePx();
    }

    /** A declared size when the attribute is a whole number of pixels ("800"); null otherwise ("50%", absent). */
    private static Integer declaredSize(String value) {
        String trimmed = value.trim();
        if (!trimmed.matches("\\d{1,6}")) {
            return null;
        }
        return Integer.valueOf(trimmed);
    }

    /** The {@code srcset} attribute (HTML "parse a srcset attribute", simplified). */
    static final class Srcset {

        /**
         * One entry of a srcset.
         *
         * @param url the address as written
         * @param width its width descriptor ({@code 800w}); null for a density descriptor or none
         */
        record Entry(String url, Integer width) {
        }

        private Srcset() {
        }

        /**
         * The largest entry: the largest width descriptor ({@code 800w}) when any entry has one, otherwise the largest
         * density ({@code 2x}, 1x when absent); the first one on a tie; null when empty.
         */
        static Entry largest(String srcset) {
            if (srcset == null || srcset.isBlank()) {
                return null;
            }
            String bestWidthUrl = null;
            double bestWidth = -1;
            String bestDensityUrl = null;
            double bestDensity = -1;
            int position = 0;
            int length = srcset.length();
            while (position < length) {
                while (position < length && (Character.isWhitespace(srcset.charAt(position))
                        || srcset.charAt(position) == ',')) {
                    position++;
                }
                if (position >= length) {
                    break;
                }
                int start = position;
                while (position < length && !Character.isWhitespace(srcset.charAt(position))) {
                    position++;
                }
                String url = srcset.substring(start, position);
                String descriptor = "";
                if (url.endsWith(",")) {
                    url = url.replaceAll(",+$", "");
                } else {
                    int descriptorStart = position;
                    int depth = 0;
                    while (position < length && (srcset.charAt(position) != ',' || depth > 0)) {
                        char c = srcset.charAt(position);
                        depth += c == '(' ? 1 : c == ')' ? -1 : 0;
                        position++;
                    }
                    descriptor = srcset.substring(descriptorStart, position).trim().toLowerCase(Locale.ROOT);
                    position++;
                }
                if (url.isEmpty()) {
                    continue;
                }
                Double width = number(descriptor, 'w');
                if (width != null) {
                    if (width > bestWidth) {
                        bestWidth = width;
                        bestWidthUrl = url;
                    }
                } else {
                    Double density = number(descriptor, 'x');
                    double value = density == null ? 1 : density;
                    if (value > bestDensity) {
                        bestDensity = value;
                        bestDensityUrl = url;
                    }
                }
            }
            if (bestWidthUrl != null) {
                return new Entry(bestWidthUrl, (int) Math.min(bestWidth, Integer.MAX_VALUE));
            }
            return bestDensityUrl == null ? null : new Entry(bestDensityUrl, null);
        }

        private static Double number(String descriptor, char unit) {
            for (String token : descriptor.split("\\s+")) {
                if (token.length() > 1 && token.charAt(token.length() - 1) == unit) {
                    try {
                        return Double.valueOf(token.substring(0, token.length() - 1));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
            return null;
        }
    }
}
