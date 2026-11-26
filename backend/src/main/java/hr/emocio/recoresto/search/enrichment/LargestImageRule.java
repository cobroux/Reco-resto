package hr.emocio.recoresto.search.enrichment;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The fixed rule that picks the page images to try when the website declares no usable sharing image (spec 007
 * FR-004 steps 2 to 4, amendment (a): no language model). Step 1 (which {@code <img>} are candidates) is done by
 * {@link WebsitePageReader}; checking each image in order is done by {@link CardEnrichmentService}.
 * <ol start="2">
 * <li>Candidates whose address or alternative text contains "logo" or "icon", in any case, are excluded.</li>
 * <li>The others are ordered by declared area, largest first; candidates with no declared area come after, in page
 * order (the sort is stable, so equal areas keep the page order too).</li>
 * <li>At most {@value #MAX_CHECKED} of them are checked.</li>
 * </ol>
 */
@Component
public class LargestImageRule {

    /** FR-004 step 4: at most 3 candidates are checked. */
    static final int MAX_CHECKED = 3;

    /** FR-004 step 2. */
    private static final List<String> EXCLUDED_WORDS = List.of("logo", "icon");

    private static final Comparator<ImageCandidate> LARGEST_FIRST = Comparator.comparing(LargestImageRule::area,
            Comparator.nullsLast(Comparator.reverseOrder()));

    /** The candidates to check, in order: at most {@value #MAX_CHECKED}, possibly none. */
    public List<ImageCandidate> toCheck(List<ImageCandidate> candidates) {
        return candidates.stream().filter(candidate -> !excluded(candidate)).sorted(LARGEST_FIRST).limit(MAX_CHECKED)
                .toList();
    }

    private static boolean excluded(ImageCandidate candidate) {
        return Stream.of(candidate.url(), candidate.alt()).filter(Objects::nonNull)
                .map(text -> text.toLowerCase(Locale.ROOT))
                .anyMatch(text -> EXCLUDED_WORDS.stream().anyMatch(text::contains));
    }

    /**
     * FR-004 step 3: width x height, where the width is the width descriptor of the chosen {@code srcset} entry when
     * there is one, else the declared {@code width}; null (no declared area) when the width or the height is unknown.
     */
    static Long area(ImageCandidate candidate) {
        Integer width = candidate.srcsetWidth() != null ? candidate.srcsetWidth() : candidate.width();
        if (width == null || candidate.height() == null) {
            return null;
        }
        return (long) width * candidate.height();
    }
}
