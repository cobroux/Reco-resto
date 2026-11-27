package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Spec 007 T5, T7, T8, T9, T10, T11, T12, T26, T28, T30, SC-002, SC-005, FR-006; amendment (b) T31, T33-T36;
 * research R12; data-model.md "State flow", "Card enrichment" ({@code photoUrl}, {@code photoOrigin}). Real reader
 * and rule, local fake website (also the Unsplash stub).
 */
class CardEnrichmentServiceTest {

    /** T8 page: dish 300x200, room 1200x800, logo 400x100. */
    private static final String T8_IMAGES = "<img src=\"/plat.jpg\" alt=\"Plat\" width=\"300\" height=\"200\">"
            + "<img src=\"/salle.jpg\" alt=\"La salle\" width=\"1200\" height=\"800\">"
            + "<img src=\"/logo.png\" width=\"400\" height=\"100\">";

    private FakeWebsite site;
    private ExecutorService executor;
    private CardEnrichmentProperties properties;
    private CardEnrichmentService service;

    @BeforeEach
    void setUp() throws Exception {
        site = new FakeWebsite();
        executor = Executors.newFixedThreadPool(8);
        properties = TestCardEnrichmentProperties.allowingPort(site.port());
        SafeWebFetcher fetcher = new SafeWebFetcher(properties, SafeWebFetcher::resolve,
                address -> address.isLoopbackAddress() || SafeWebFetcher.isPublic(address));
        service = new CardEnrichmentService(fetcher, new WebsitePageReader(properties), new LargestImageRule(),
                UnsplashStub.disabled(properties), properties, executor);
    }

    @AfterEach
    void tearDown() {
        site.close();
        executor.shutdownNow();
    }

    private WebsiteAddress website(String path) {
        return WebsiteAddress.of(site.url(path), properties.allowedPorts()).orElseThrow();
    }

    private String url(String path) {
        return site.url(path).toString();
    }

    private CardEnrichment enrich(String path) {
        return service.enrich(website(path), null);
    }

    private static String page(String head, String body) {
        return "<html><head><title>Chez Exemple secret title</title>" + head + "</head><body>" + body
                + "</body></html>";
    }

    /** T5: a declared sharing image answering as an image is used; the page images are not considered. */
    @Test
    void aSharingImageAnsweringAsAnImageIsUsed() {
        site.html("/", page("<meta property=\"og:image\" content=\"/partage.jpg\">", T8_IMAGES))
                .image("/partage.jpg").image("/salle.jpg").image("/plat.jpg");

        CardEnrichment enrichment = enrich("/");

        assertThat(enrichment.photoUrl()).isEqualTo(url("/partage.jpg"));
        assertThat(enrichment.photoOrigin()).isEqualTo(EnrichmentOutcome.SHARING_IMAGE);
        assertThat(site.paths()).containsExactly("/", "/partage.jpg");
    }

    /** T8, FR-006, SC-005: no sharing image, the largest page image is used. */
    @Test
    void withoutSharingImageTheLargestPageImageIsUsed() {
        site.html("/", page("", T8_IMAGES)).image("/plat.jpg").image("/salle.jpg").image("/logo.png");

        List<String> logs = infoLogs(() -> {
            CardEnrichment enrichment = enrich("/");
            assertThat(enrichment.photoUrl()).isEqualTo(url("/salle.jpg"));
            assertThat(enrichment.photoOrigin()).isEqualTo(EnrichmentOutcome.LARGEST_PAGE_IMAGE);
        });

        assertThat(logs).singleElement().asString().contains("LARGEST_PAGE_IMAGE");
        assertThat(site.paths()).containsExactly("/", "/salle.jpg");
    }

    /** T7: a broken sharing image is treated as no sharing image. */
    @Test
    void aBrokenSharingImageFallsBackToTheLargestPageImage() {
        site.html("/", page("<meta property=\"og:image\" content=\"/missing.jpg\">", T8_IMAGES)).image("/salle.jpg");

        CardEnrichment enrichment = enrich("/");

        assertThat(enrichment.photoUrl()).isEqualTo(url("/salle.jpg"));
        assertThat(enrichment.photoOrigin()).isEqualTo(EnrichmentOutcome.LARGEST_PAGE_IMAGE);
    }

    /** T7: a sharing image that is not an image (HTML page) is not used. */
    @Test
    void aSharingImageThatIsNotAnImageFallsBackToTheLargestPageImage() {
        site.html("/", page("<meta property=\"og:image\" content=\"/page.html\">", T8_IMAGES))
                .html("/page.html", "<html></html>").image("/salle.jpg");

        assertThat(enrich("/").photoOrigin()).isEqualTo(EnrichmentOutcome.LARGEST_PAGE_IMAGE);
    }

    /** T9: the largest candidate cannot be displayed, the second one can. */
    @Test
    void aBrokenLargestImageGivesTheSecondCandidate() {
        site.html("/", page("", T8_IMAGES)).image("/plat.jpg");

        assertThat(enrich("/").photoUrl()).isEqualTo(url("/plat.jpg"));
        assertThat(site.paths()).containsExactly("/", "/salle.jpg", "/plat.jpg");
    }

    /** T10: only excluded images; no image is even checked. */
    @Test
    void onlyExcludedImagesGiveNoPhoto() {
        site.html("/", page("", "<img src=\"/logo.svg\"><img src=\"/favicon.ico\">"
                + "<img src=\"/badge.jpg\" width=\"50\" height=\"50\"><img src=\"/brand-logo.png\">"))
                .image("/badge.jpg").image("/brand-logo.png");

        List<String> logs = infoLogs(() -> assertThat(enrich("/")).isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("NO_IMAGE");
        assertThat(site.paths()).containsExactly("/");
    }

    /** T11. */
    @Test
    void noSharingImageAndNoImageGivesNoPhoto() {
        site.html("/", page("", "<p>Bienvenue</p>"));

        List<String> logs = infoLogs(() -> assertThat(enrich("/")).isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("NO_IMAGE");
    }

    /** T12: no declared size, the first image is broken: the second image of the page. */
    @Test
    void withoutDeclaredSizeThePageOrderDecides() {
        site.html("/", page("", "<img src=\"/1.jpg\"><img src=\"/2.jpg\"><img src=\"/3.jpg\">"))
                .image("/2.jpg").image("/3.jpg");

        assertThat(enrich("/").photoUrl()).isEqualTo(url("/2.jpg"));
    }

    /** T28: the 4 largest images are broken, the 5th is fine: at most 3 checked, no photo. */
    @Test
    void atMostThreeImagesAreChecked() {
        StringBuilder images = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            images.append("<img src=\"/").append(i).append(".jpg\" width=\"").append(1000 - i * 100)
                    .append("\" height=\"500\">");
        }
        site.html("/", page("", images.toString())).image("/5.jpg");

        List<String> logs = infoLogs(() -> assertThat(enrich("/")).isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("NO_DISPLAYABLE_IMAGE");
        assertThat(site.paths()).containsExactly("/", "/1.jpg", "/2.jpg", "/3.jpg");
    }

    /** T26. */
    @Test
    void aRefusedConnectionGivesNoPhoto() {
        WebsiteAddress closed = website("/");
        site.close();

        List<String> logs = infoLogs(() -> assertThat(service.enrich(closed, null))
                .isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("UNREACHABLE");
    }

    /** T30: the redirected page's sharing image, resolved against the final address. */
    @Test
    void aRedirectLeadsToThePageDeclaringTheSharingImage() {
        site.redirect("/", "/fr/")
                .html("/fr/", page("<meta property=\"og:image\" content=\"accueil.jpg\">", ""))
                .image("/fr/accueil.jpg");

        CardEnrichment enrichment = enrich("/");

        assertThat(enrichment.photoUrl()).isEqualTo(url("/fr/accueil.jpg"));
        assertThat(enrichment.photoOrigin()).isEqualTo(EnrichmentOutcome.SHARING_IMAGE);
    }

    @Test
    void aNonHtmlAnswerGivesNoPhoto() {
        site.on("/", exchange -> FakeWebsite.send(exchange, 200, "application/pdf", new byte[] {1}));

        List<String> logs = infoLogs(() -> assertThat(enrich("/")).isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("NOT_HTML");
    }

    @Test
    void aBlockedAddressGivesNoPhoto() {
        SafeWebFetcher strict = new SafeWebFetcher(properties);
        CardEnrichmentService production = new CardEnrichmentService(strict, new WebsitePageReader(properties),
                new LargestImageRule(), UnsplashStub.disabled(properties), properties, executor);
        site.html("/", page("", ""));

        List<String> logs = infoLogs(() -> assertThat(production.enrich(website("/"), null))
                .isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("BLOCKED_ADDRESS");
        assertThat(site.requests()).isEmpty();
    }

    /** Research R12: one INFO line with outcome, duration and host; never page content. */
    @Test
    void oneInfoLineGivesTheOutcomeTheDurationAndTheHostOnly() {
        site.html("/", page("<meta property=\"og:image\" content=\"/salle.jpg\">", "<p>Menu secret du jour</p>"))
                .image("/salle.jpg");

        List<String> logs = infoLogs(() -> service.enrich(website("/"), null));

        assertThat(logs).singleElement().asString().contains("SHARING_IMAGE").contains("ms").contains("127.0.0.1")
                .doesNotContain("Menu secret").doesNotContain("secret title")
                .doesNotContain("/salle.jpg");
    }

    @Test
    void theResponseHasAPhotoOriginExactlyWhenItHasAPhoto() {
        assertThat(CardEnrichment.none().photoUrl()).isNull();
        assertThat(CardEnrichment.none().photoOrigin()).isNull();
        assertThat(CardEnrichment.photo(URI.create("https://x.test/a.jpg").toString(),
                EnrichmentOutcome.LARGEST_PAGE_IMAGE).photoOrigin()).isEqualTo(EnrichmentOutcome.LARGEST_PAGE_IMAGE);
    }

    // ---- Amendment (b): Unsplash cuisine photo as the last resort (T31, T33-T36, T26 / plan OQ-6) ----

    /** The service with Unsplash enabled, the local stub answering on the same fake site. */
    private CardEnrichmentService withUnsplash() {
        CardEnrichmentProperties unsplash = UnsplashStub.pointedAt(site, properties, UnsplashStub.KEY);
        SafeWebFetcher fetcher = new SafeWebFetcher(unsplash, SafeWebFetcher::resolve,
                address -> address.isLoopbackAddress() || SafeWebFetcher.isPublic(address));
        return new CardEnrichmentService(fetcher, new WebsitePageReader(unsplash), new LargestImageRule(),
                UnsplashStub.cuisinePhotos(unsplash, Clock.systemUTC()), unsplash, executor);
    }

    private List<String> unsplashQueries() {
        return site.requests().stream().filter(r -> r.path().equals(UnsplashStub.SEARCH_PATH))
                .map(FakeWebsite.Request::query).toList();
    }

    /** T31: no usable page image, the cuisine photo with its credit; query "Restaurant italien", lang fr. */
    @Test
    void withoutUsableImageTheCuisinePhotoIsUsed() {
        site.html("/", page("", "<p>Bienvenue</p>"));
        UnsplashStub.answers(site, UnsplashStub.answerOf(15));
        CardEnrichmentService service = withUnsplash();

        List<String> logs = infoLogs(() -> {
            CardEnrichment enrichment = service.enrich(website("/"), "Restaurant italien");
            assertThat(enrichment.photoOrigin()).isEqualTo(EnrichmentOutcome.UNSPLASH_CUISINE_PHOTO);
            assertThat(enrichment.photoUrl()).startsWith("https://images.unsplash.test/photo-");
            assertThat(enrichment.credit().unsplashUrl())
                    .isEqualTo("https://unsplash.com/?utm_source=reco_resto&utm_medium=referral");
        });

        assertThat(unsplashQueries()).containsExactly("query=Restaurant italien&per_page=15&lang=fr");
        assertThat(logs).singleElement().asString().contains("UNSPLASH_CUISINE_PHOTO")
                .doesNotContain(UnsplashStub.KEY);
    }

    /** AS7: the checked page images all fail, the cuisine photo is used. */
    @Test
    void afterBrokenPageImagesTheCuisinePhotoIsUsed() {
        site.html("/", page("<meta property=\"og:image\" content=\"/missing.jpg\">", T8_IMAGES));
        UnsplashStub.answers(site, UnsplashStub.answerOf(15));

        CardEnrichment enrichment = withUnsplash().enrich(website("/"), "Pizzeria");

        assertThat(enrichment.photoOrigin()).isEqualTo(EnrichmentOutcome.UNSPLASH_CUISINE_PHOTO);
        assertThat(site.paths()).containsExactly("/", "/missing.jpg", "/salle.jpg", "/plat.jpg",
                UnsplashStub.SEARCH_PATH);
    }

    /** T33. */
    @Test
    void noUnsplashResultGivesNoPhoto() {
        site.html("/", page("", ""));
        UnsplashStub.answers(site, UnsplashStub.answer());
        CardEnrichmentService service = withUnsplash();

        List<String> logs = infoLogs(() -> assertThat(service.enrich(website("/"), "Pizzeria"))
                .isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("NO_CUISINE_PHOTO");
    }

    /** T34: limit reached. */
    @Test
    void aRefusedUnsplashRequestGivesNoPhoto() {
        site.html("/", page("", ""));
        site.on(UnsplashStub.SEARCH_PATH, exchange -> FakeWebsite.send(exchange, 429, "text/plain", new byte[0]));
        CardEnrichmentService service = withUnsplash();

        List<String> logs = infoLogs(() -> assertThat(service.enrich(website("/"), "Pizzeria"))
                .isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("UNSPLASH_UNAVAILABLE");
    }

    /** T35: a displayable sharing image, no Unsplash request. */
    @Test
    void aSharingImageSendsNoUnsplashRequest() {
        site.html("/", page("<meta property=\"og:image\" content=\"/partage.jpg\">", "")).image("/partage.jpg");
        UnsplashStub.answers(site, UnsplashStub.answerOf(15));

        assertThat(withUnsplash().enrich(website("/"), "Pizzeria").photoOrigin())
                .isEqualTo(EnrichmentOutcome.SHARING_IMAGE);
        assertThat(unsplashQueries()).isEmpty();
    }

    /** A page image found, no Unsplash request. */
    @Test
    void aPageImageSendsNoUnsplashRequest() {
        site.html("/", page("", T8_IMAGES)).image("/salle.jpg");
        UnsplashStub.answers(site, UnsplashStub.answerOf(15));

        assertThat(withUnsplash().enrich(website("/"), "Pizzeria").photoOrigin())
                .isEqualTo(EnrichmentOutcome.LARGEST_PAGE_IMAGE);
        assertThat(unsplashQueries()).isEmpty();
    }

    /** T36: no key, no request; the website outcome is kept. */
    @Test
    void withoutKeyNoUnsplashRequestIsSent() {
        site.html("/", page("", ""));
        UnsplashStub.answers(site, UnsplashStub.answerOf(15));

        List<String> logs = infoLogs(() -> assertThat(service.enrich(website("/"), "Pizzeria"))
                .isEqualTo(CardEnrichment.none()));

        assertThat(logs).singleElement().asString().contains("NO_IMAGE");
        assertThat(unsplashQueries()).isEmpty();
    }

    /** T26, plan OQ-6: a website that cannot be read gets no cuisine photo. */
    @Test
    void anUnreadableWebsiteSendsNoUnsplashRequest() {
        site.on("/", exchange -> FakeWebsite.send(exchange, 500, "text/html", new byte[0]));
        UnsplashStub.answers(site, UnsplashStub.answerOf(15));

        assertThat(withUnsplash().enrich(website("/"), "Pizzeria")).isEqualTo(CardEnrichment.none());
        assertThat(unsplashQueries()).isEmpty();
    }

    private static List<String> infoLogs(Runnable action) {
        return logsOf(CardEnrichmentService.class, Level.INFO, () -> {
            action.run();
            return null;
        });
    }

    static List<String> logsOf(Class<?> type, Level level, Supplier<?> action) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        Level before = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(level);
        try {
            action.get();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(before);
        }
        return appender.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(level))
                .map(ILoggingEvent::getFormattedMessage).toList();
    }
}
