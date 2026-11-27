package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 007 FR-004 steps 2 to 4 (amendment (a)), T8, T10, T12, T28; data-model.md "Largest image rule". Step 1 (which
 * {@code <img>} are candidates) is {@link WebsitePageReaderTest}; checking the images is
 * {@link CardEnrichmentServiceTest}.
 */
class LargestImageRuleTest {

    private final LargestImageRule rule = new LargestImageRule();

    private static ImageCandidate sized(String url, String alt, Integer width, Integer height) {
        return new ImageCandidate(url, alt, width, height, null);
    }

    private static ImageCandidate unsized(String url) {
        return new ImageCandidate(url, null, null, null, null);
    }

    private List<String> order(ImageCandidate... candidates) {
        return rule.toCheck(List.of(candidates)).stream().map(ImageCandidate::url).toList();
    }

    /** T8: the 1200x800 room comes before the 300x200 dish; the 400x100 logo is excluded. */
    @Test
    void theLargestDeclaredAreaComesFirstAndLogosAreExcluded() {
        assertThat(order(sized("https://r.test/plat.jpg", "Plat", 300, 200),
                sized("https://r.test/salle.jpg", "Salle", 1200, 800),
                sized("https://r.test/logo.png", null, 400, 100)))
                .containsExactly("https://r.test/salle.jpg", "https://r.test/plat.jpg");
    }

    /** Step 2: "logo" or "icon" in the address or the alternative text, any case. */
    @Test
    void logoAndIconInTheAddressOrTheAltTextAreExcludedInAnyCase() {
        assertThat(order(sized("https://r.test/brand-LOGO.png", null, 900, 900),
                sized("https://r.test/Icons/menu.png", null, 900, 900),
                sized("https://r.test/a.jpg", "Notre Logo", 900, 900),
                sized("https://r.test/b.jpg", "ICON", 900, 900),
                sized("https://r.test/c.jpg", "La salle", 900, 900))).containsExactly("https://r.test/c.jpg");
    }

    /** T10 (after step 1): nothing left gives nothing to check. */
    @Test
    void onlyExcludedImagesLeaveNothingToCheck() {
        assertThat(order(unsized("https://r.test/brand-logo.png"))).isEmpty();
        assertThat(rule.toCheck(List.of())).isEmpty();
    }

    /** T12: images with no declared size keep the page order, after the sized ones. */
    @Test
    void imagesWithoutDeclaredSizeComeAfterInPageOrder() {
        assertThat(order(unsized("https://r.test/1.jpg"), sized("https://r.test/small.jpg", null, 200, 200),
                unsized("https://r.test/2.jpg")))
                .containsExactly("https://r.test/small.jpg", "https://r.test/1.jpg", "https://r.test/2.jpg");
    }

    /** Equal areas keep the page order. */
    @Test
    void equalAreasKeepThePageOrder() {
        assertThat(order(sized("https://r.test/a.jpg", null, 400, 300), sized("https://r.test/b.jpg", null, 300, 400)))
                .containsExactly("https://r.test/a.jpg", "https://r.test/b.jpg");
    }

    /** Step 3: the srcset width descriptor is the width of the area, in place of the width attribute. */
    @Test
    void theSrcsetWidthDescriptorIsTheWidthOfTheArea() {
        assertThat(order(sized("https://r.test/a.jpg", null, 1000, 500),
                new ImageCandidate("https://r.test/b-1600.jpg", null, 400, 500, 1600)))
                .containsExactly("https://r.test/b-1600.jpg", "https://r.test/a.jpg");
    }

    /** An area needs a width and a height: one of them alone is no declared size. */
    @Test
    void aSingleDimensionIsNoDeclaredSize() {
        assertThat(order(sized("https://r.test/w.jpg", null, 2000, null),
                new ImageCandidate("https://r.test/s.jpg", null, null, null, 2000),
                sized("https://r.test/both.jpg", null, 200, 200)))
                .containsExactly("https://r.test/both.jpg", "https://r.test/w.jpg", "https://r.test/s.jpg");
    }

    /** Step 4, T28: at most 3 candidates are checked. */
    @Test
    void atMostThreeCandidatesAreChecked() {
        assertThat(order(sized("https://r.test/5.jpg", null, 500, 100), sized("https://r.test/1.jpg", null, 900, 100),
                sized("https://r.test/2.jpg", null, 800, 100), sized("https://r.test/4.jpg", null, 600, 100),
                sized("https://r.test/3.jpg", null, 700, 100)))
                .containsExactly("https://r.test/1.jpg", "https://r.test/2.jpg", "https://r.test/3.jpg");
    }
}
