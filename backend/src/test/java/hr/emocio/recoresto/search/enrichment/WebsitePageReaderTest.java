package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Spec 007 T5, T6, T8, T10, T11, FR-004 step 1, FR-006; PO-1, PO-3, PO-5, OQ-3; data-model.md "Website page"
 * ({@code finalUrl}, {@code sharingImage}, {@code candidates}) and "Image candidate" ({@code url}, {@code alt},
 * {@code width}, {@code height}, {@code srcsetWidth}). Pages under {@code src/test/resources/enrichment-pages} are
 * invented.
 */
class WebsitePageReaderTest {

    private static final URI FINAL_URL = URI.create("https://www.example-restaurant.test/fr/accueil");

    private final WebsitePageReader reader = new WebsitePageReader(TestCardEnrichmentProperties.defaults());

    private static byte[] fixture(String name) {
        try (InputStream in = WebsitePageReaderTest.class.getResourceAsStream("/enrichment-pages/" + name)) {
            return in.readAllBytes();
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("missing fixture " + name, e);
        }
    }

    private WebsitePage read(String name) {
        return reader.read(new FetchedPage(FINAL_URL, fixture(name), "UTF-8"));
    }

    @Test
    void theFinalAddressIsKept() {
        assertThat(read("og-absolute.html").finalUrl()).isEqualTo(FINAL_URL);
    }

    /** T5, PO-5: the first og:image wins over the later ones and over twitter:image. */
    @Test
    void theSharingImageIsTheFirstOgImage() {
        assertThat(read("og-absolute.html").sharingImage()).isEqualTo("https://cdn.example.test/salle.jpg");
    }

    /** T6: resolved against the final page address. */
    @Test
    void aRelativeSharingImageIsResolvedAgainstTheFinalAddress() {
        assertThat(read("og-relative.html").sharingImage())
                .isEqualTo("https://www.example-restaurant.test/images/salle.jpg");
    }

    /** T6: and against the page {@code <base href>} when present. */
    @Test
    void aRelativeSharingImageIsResolvedAgainstTheBaseHref() {
        WebsitePage page = read("og-relative-base.html");

        assertThat(page.sharingImage()).isEqualTo("https://static.example.test/site/img/salle.jpg");
        assertThat(page.candidates()).extracting(ImageCandidate::url)
                .containsExactly("https://static.example.test/site/img/plat.jpg");
    }

    /** PO-5: a blank og:image is skipped, then og:image:secure_url, og:image:url, twitter:image, image_src. */
    @Test
    void theOtherDeclaredSharingImagesAreFallbacksInTheDecidedOrder() {
        assertThat(read("og-blank-then-secure-url.html").sharingImage())
                .isEqualTo("https://cdn.example.test/secure.jpg");
        assertThat(read("og-url.html").sharingImage()).isEqualTo("https://www.example-restaurant.test/og-url.jpg");
        assertThat(read("twitter-image.html").sharingImage()).isEqualTo("https://cdn.example.test/twitter.jpg");
        assertThat(read("image-src-link.html").sharingImage())
                .isEqualTo("https://www.example-restaurant.test/image-src.jpg");
    }

    @Test
    void aPageWithoutDeclaredImageHasNoSharingImage() {
        assertThat(read("images.html").sharingImage()).isNull();
        assertThat(read("no-images.html").sharingImage()).isNull();
    }

    /**
     * FR-004 step 1, PO-3, OQ-3: one candidate per {@code <img>} (largest srcset entry, else data-src, else
     * data-lazy-src, else src); .svg / .ico, images declared under 100 px, data: and non-http(s) addresses dropped;
     * deduplicated; page order. The width descriptor of the chosen srcset entry is kept.
     */
    @Test
    void candidatesFollowTheDecidedRules() {
        WebsitePage page = read("images.html");

        assertThat(page.candidates()).extracting(ImageCandidate::url, ImageCandidate::alt, ImageCandidate::width,
                ImageCandidate::height, ImageCandidate::srcsetWidth).containsExactly(
                        tuple("https://www.example-restaurant.test/img/plat.jpg", "Notre plat du jour", 800, 600, null),
                        tuple("https://www.example-restaurant.test/img/salle.jpg", "La salle", null, null, null),
                        tuple("https://cdn.example.test/terrasse.jpg", "Terrasse", null, null, null),
                        tuple("https://www.example-restaurant.test/img/facade-1200.jpg", "Facade", null, null, 1200),
                        tuple("https://www.example-restaurant.test/fr/relative/chef-cuisine.JPG", "En cuisine", null,
                                null, null),
                        tuple("https://www.example-restaurant.test/img/data-src-over-lazy.jpg", null, null, null, null),
                        tuple("https://www.example-restaurant.test/img/x2.jpg", null, null, null, null),
                        tuple("https://cdn.example.test/w_800,h_600/a.jpg", null, null, null, 800),
                        tuple("https://www.example-restaurant.test/img/exact.jpg", "Exact", 100, 100, null),
                        tuple("https://www.example-restaurant.test/img/percent.jpg", "Percent", null, null, null));
    }

    /** Amendment (a): no limit on the number of candidates (the largest image may be anywhere in the page). */
    @Test
    void everyImageOfThePageIsACandidate() {
        WebsitePage page = read("many-images.html");

        assertThat(page.candidates()).hasSize(25);
        assertThat(page.candidates().getFirst().url()).endsWith("/img/photo-01.jpg");
        assertThat(page.candidates().getLast().url()).endsWith("/img/photo-25.jpg");
    }

    /** T8: the page images with their declared sizes, ready for the rule; read then ordered, the room comes first. */
    @Test
    void theT8PageGivesTheRoomFirstOnceOrdered() {
        WebsitePage page = read("largest-image.html");

        assertThat(page.candidates()).extracting(ImageCandidate::width, ImageCandidate::height)
                .containsExactly(tuple(300, 200), tuple(1200, 800), tuple(400, 100));
        assertThat(new LargestImageRule().toCheck(page.candidates())).extracting(ImageCandidate::url).containsExactly(
                "https://www.example-restaurant.test/img/salle.jpg", "https://www.example-restaurant.test/img/plat.jpg");
    }

    /** T10: only logo.svg, favicon.ico, a 50x50 image and brand-logo.png: nothing left after the rule. */
    @Test
    void theT10PageLeavesNoImageToCheck() {
        WebsitePage page = read("only-excluded-images.html");

        assertThat(page.candidates()).extracting(ImageCandidate::url)
                .containsExactly("https://www.example-restaurant.test/img/brand-logo.png");
        assertThat(new LargestImageRule().toCheck(page.candidates())).isEmpty();
    }

    /** T11. */
    @Test
    void aPageWithoutImageHasNoCandidate() {
        assertThat(read("no-images.html").candidates()).isEmpty();
    }

    /** PO-1: a page cut at 1 MiB still gives the og:image of its head. */
    @Test
    void aCutPageStillGivesItsSharingImage() {
        byte[] whole = ("<html><head><meta property=\"og:image\" content=\"/big.jpg\"></head><body><!-- generated,"
                + " NOT real data -->" + "<p>texte</p>".repeat(200_000) + "<img src=\"/late.jpg\"></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(whole.length).isGreaterThan(1_048_576);
        byte[] cut = Arrays.copyOf(whole, 1_048_576);

        WebsitePage page = reader.read(new FetchedPage(FINAL_URL, cut, null));

        assertThat(page.sharingImage()).isEqualTo("https://www.example-restaurant.test/big.jpg");
        assertThat(page.candidates()).isEmpty();
    }

    @Test
    void theCharsetIsDetectedWhenTheHeaderGivesNone() {
        byte[] latin1 = "<html><head><meta charset=\"iso-8859-1\"></head><body><img src=\"/a.jpg\" alt=\"Café\">"
                .concat("</body></html>").getBytes(StandardCharsets.ISO_8859_1);

        assertThat(reader.read(new FetchedPage(FINAL_URL, latin1, null)).candidates().getFirst().alt())
                .isEqualTo("Café");
    }
}
