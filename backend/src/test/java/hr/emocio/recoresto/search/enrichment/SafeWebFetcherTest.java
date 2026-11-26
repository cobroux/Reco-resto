package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 007 research R4, R5, R6; PO-1, PO-6, PO-7, PO-8; part of T26 and T30. Every request goes to a local fake website;
 * the strict address check of production is replaced only where a test needs to reach the loopback fake site.
 */
class SafeWebFetcherTest {

    private FakeWebsite site;
    /** Reaches the local fake site: loopback allowed, everything else per the strict production rule. */
    private SafeWebFetcher fetcher;

    @BeforeEach
    void setUp() throws Exception {
        site = new FakeWebsite();
        fetcher = loopbackAllowed(TestCardEnrichmentProperties.allowingPort(site.port()));
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    private static SafeWebFetcher loopbackAllowed(CardEnrichmentProperties properties) {
        return new SafeWebFetcher(properties, SafeWebFetcher::resolve,
                address -> address.isLoopbackAddress() || SafeWebFetcher.isPublic(address));
    }

    /** The production rule, with a fake resolver so that no DNS request is made. */
    private static SafeWebFetcher strict(CardEnrichmentProperties properties, String... addresses) {
        return new SafeWebFetcher(properties, host -> Arrays.stream(addresses)
                .map(SafeWebFetcherTest::address).toList(), SafeWebFetcher::isPublic);
    }

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Deadline fiveSeconds() {
        return Deadline.after(Duration.ofSeconds(5));
    }

    private EnrichmentOutcome failure(SafeWebFetcher used, URI url) {
        try {
            used.page(url, fiveSeconds());
        } catch (FetchException e) {
            return e.outcome();
        }
        throw new AssertionError("the fetch was expected to fail");
    }

    // --- address check (research R5) -------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.1.2.3", "::1", "0.0.0.0", "0.1.2.3", "::", "10.0.0.1", "10.255.255.255",
            "172.16.0.1", "172.31.255.254", "192.168.1.1", "169.254.0.1", "169.254.169.254", "fe80::1", "100.64.0.1",
            "100.127.255.254", "fc00::1", "fd12:3456::1", "224.0.0.1", "239.255.255.250", "ff02::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254", "::10.0.0.1", "64:ff9b::a00:1",
            "64:ff9b::7f00:1", "64:ff9b::a9fe:a9fe", "2002:a00:1::", "2002:7f00:1::1", "2002:a9fe:a9fe::1"})
    void nonPublicAddressesAreRefused(String literal) {
        assertThat(SafeWebFetcher.isPublic(address(literal))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "8.8.8.8", "172.32.0.1", "100.128.0.1", "2606:4700::1111",
            "::ffff:93.184.216.34", "64:ff9b::808:808", "2002:808:808::1"})
    void publicAddressesAreAccepted(String literal) {
        assertThat(SafeWebFetcher.isPublic(address(literal))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "::1", "0.0.0.0", "10.1.2.3", "172.20.0.5", "192.168.0.10", "169.254.169.254",
            "fe80::1", "100.64.1.1", "fc00::5", "224.0.0.1", "::ffff:192.168.0.1"})
    void aWebsiteResolvingToANonPublicAddressIsBlockedBeforeAnyConnection(String literal) {
        SafeWebFetcher production = strict(TestCardEnrichmentProperties.allowingPort(site.port()), literal);

        assertThat(failure(production, site.url("/"))).isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
        assertThat(site.requests()).isEmpty();
    }

    @Test
    void theLoopbackFakeSiteItselfIsBlockedByTheProductionRule() {
        SafeWebFetcher production = new SafeWebFetcher(
                TestCardEnrichmentProperties.allowingPort(site.port()));
        site.html("/", "<html></html>");

        assertThat(failure(production, site.url("/"))).isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
        assertThat(failure(production, URI.create("http://localhost:" + site.port() + "/")))
                .isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
        assertThat(site.requests()).isEmpty();
    }

    /**
     * PR #15 review: DNS rebinding. The host answers a public address to the check, then a private one: the connection
     * must use only checked addresses, so nothing is reached.
     */
    @Test
    void aHostWhoseAnswerChangesAfterTheCheckIsNeverReached() {
        AtomicInteger lookups = new AtomicInteger();
        SafeWebFetcher production = new SafeWebFetcher(TestCardEnrichmentProperties.allowingPort(site.port()),
                host -> List.of(address(lookups.incrementAndGet() == 1 ? "93.184.216.34" : "127.0.0.1")),
                SafeWebFetcher::isPublic);
        site.html("/", "<html></html>");

        assertThat(failure(production, URI.create("http://rebind.test:" + site.port() + "/")))
                .isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
        assertThat(site.requests()).isEmpty();
    }

    @Test
    void aHostResolvingToSeveralAddressesOfWhichOneIsPrivateIsBlocked() {
        SafeWebFetcher production = strict(TestCardEnrichmentProperties.defaults(), "93.184.216.34", "10.0.0.7");

        assertThat(failure(production, URI.create("https://restaurant.test/")))
                .isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://restaurant.test/", "file:///etc/passwd", "https://user:pw@restaurant.test/"})
    void aNonHttpSchemeOrUserInfoIsBlocked(String url) {
        SafeWebFetcher production = strict(TestCardEnrichmentProperties.defaults(), "93.184.216.34");

        assertThat(failure(production, URI.create(url))).isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
    }

    /** PO-8. */
    @Test
    void aPortOtherThan80Or443IsBlocked() {
        SafeWebFetcher onlyDefaultPorts = loopbackAllowed(TestCardEnrichmentProperties.defaults());
        site.html("/", "<html></html>");

        assertThat(failure(onlyDefaultPorts, site.url("/"))).isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
        assertThat(site.requests()).isEmpty();
    }

    // --- redirects (PO-7, T30) -------------------------------------------------------------------------------------

    @Test
    void redirectsAreFollowedAndTheFinalAddressIsReturned() throws Exception {
        site.redirect("/", "/fr/").redirect("/fr/", site.url("/fr/accueil").toString())
                .html("/fr/accueil", "<html><title>Accueil</title></html>");

        FetchedPage page = fetcher.page(site.url("/"), fiveSeconds());

        assertThat(page.finalUrl()).isEqualTo(site.url("/fr/accueil"));
        assertThat(new String(page.body(), StandardCharsets.UTF_8)).contains("Accueil");
        assertThat(site.paths()).containsExactly("/", "/fr/", "/fr/accueil");
    }

    @Test
    void aRedirectToAPrivateAddressIsBlockedBeforeConnecting() {
        site.redirect("/", "http://10.1.2.3/admin");

        assertThat(failure(fetcher, site.url("/"))).isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
        assertThat(site.paths()).containsExactly("/");
    }

    @Test
    void aRedirectToTheMetadataAddressIsBlocked() {
        site.redirect("/", "http://169.254.169.254/latest/meta-data/");

        assertThat(failure(fetcher, site.url("/"))).isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
    }

    @Test
    void aRedirectToAnotherSchemeIsBlocked() {
        site.redirect("/", "file:///etc/passwd");

        assertThat(failure(fetcher, site.url("/"))).isEqualTo(EnrichmentOutcome.BLOCKED_ADDRESS);
    }

    @Test
    void atMostFiveRedirectsAreFollowed() throws Exception {
        for (int i = 0; i < 5; i++) {
            site.redirect("/r" + i, "/r" + (i + 1));
        }
        site.html("/r5", "<html>end</html>");
        FetchedPage page = fetcher.page(site.url("/r0"), fiveSeconds());
        assertThat(page.finalUrl()).isEqualTo(site.url("/r5"));

        site.redirect("/r5", "/r6").html("/r6", "<html>too far</html>");
        assertThat(failure(fetcher, site.url("/r0"))).isEqualTo(EnrichmentOutcome.UNREACHABLE);
        assertThat(site.paths()).doesNotContain("/r6");
    }

    // --- body (PO-1, R4) -------------------------------------------------------------------------------------------

    @Test
    void theBodyIsCutAtOneMebibyteNotRejected() throws Exception {
        byte[] big = ("<html><head><meta property=\"og:image\" content=\"/a.jpg\"></head><body>"
                + "x".repeat(2 * 1024 * 1024) + "</body></html>").getBytes(StandardCharsets.UTF_8);
        site.on("/", exchange -> FakeWebsite.send(exchange, 200, "text/html", big));

        FetchedPage page = fetcher.page(site.url("/"), fiveSeconds());

        assertThat(page.body()).hasSize(1_048_576);
        assertThat(new String(page.body(), StandardCharsets.UTF_8)).startsWith("<html><head><meta property");
    }

    @Test
    void theCharsetOfTheContentTypeIsKept() throws Exception {
        site.on("/", exchange -> FakeWebsite.send(exchange, 200, "text/html; charset=ISO-8859-1",
                "<html>café</html>".getBytes(StandardCharsets.ISO_8859_1)));

        assertThat(fetcher.page(site.url("/"), fiveSeconds()).charset()).isEqualToIgnoringCase("ISO-8859-1");
    }

    @Test
    void aNonHtmlAnswerIsNotHtml() {
        site.on("/", exchange -> FakeWebsite.send(exchange, 200, "application/pdf", new byte[] {1}));
        site.on("/none", exchange -> FakeWebsite.send(exchange, 200, "", new byte[] {1}));

        assertThat(failure(fetcher, site.url("/"))).isEqualTo(EnrichmentOutcome.NOT_HTML);
        assertThat(failure(fetcher, site.url("/none"))).isEqualTo(EnrichmentOutcome.NOT_HTML);
    }

    @Test
    void anXhtmlAnswerIsHtml() throws Exception {
        site.on("/", exchange -> FakeWebsite.send(exchange, 200, "application/xhtml+xml",
                "<html/>".getBytes(StandardCharsets.UTF_8)));

        assertThat(fetcher.page(site.url("/"), fiveSeconds()).body()).isNotEmpty();
    }

    /** T26. */
    @Test
    void aRefusedConnectionOrAnErrorStatusIsUnreachable() {
        int closedPort = site.port();
        site.close();
        assertThat(failure(fetcher, URI.create("http://127.0.0.1:" + closedPort + "/")))
                .isEqualTo(EnrichmentOutcome.UNREACHABLE);
    }

    @Test
    void anErrorStatusIsUnreachable() {
        site.on("/500", exchange -> FakeWebsite.send(exchange, 500, "text/html", "<html/>".getBytes()));

        assertThat(failure(fetcher, site.url("/missing"))).isEqualTo(EnrichmentOutcome.UNREACHABLE);
        assertThat(failure(fetcher, site.url("/500"))).isEqualTo(EnrichmentOutcome.UNREACHABLE);
    }

    @Test
    void anUnknownHostIsUnreachable() {
        SafeWebFetcher unknown = new SafeWebFetcher(TestCardEnrichmentProperties.defaults(),
                host -> {
                    throw new UnknownHostException(host);
                }, SafeWebFetcher::isPublic);

        assertThat(failure(unknown, URI.create("https://nowhere.test/"))).isEqualTo(EnrichmentOutcome.UNREACHABLE);
    }

    // --- headers (PO-6) and timeout (R6) ---------------------------------------------------------------------------

    @Test
    void requestsSayWhoTheyAreAndRobotsTxtIsNeverRead() throws Exception {
        site.html("/", "<html></html>").image("/a.jpg");

        fetcher.page(site.url("/"), fiveSeconds());
        fetcher.isImage(site.url("/a.jpg"), fiveSeconds());

        FakeWebsite.Request page = site.requests().getFirst();
        assertThat(page.headers().getFirst("User-Agent")).isEqualTo("RecoResto/0.1 (proof of concept)");
        assertThat(page.headers().getFirst("Accept")).isEqualTo("text/html");
        assertThat(site.requests().get(1).headers().getFirst("User-Agent"))
                .isEqualTo("RecoResto/0.1 (proof of concept)");
        assertThat(site.paths()).containsExactly("/", "/a.jpg").doesNotContain("/robots.txt");
    }

    @Test
    void eachRequestStopsWhenTheBudgetIsSpent() {
        site.on("/slow", exchange -> {
            FakeWebsite.sleep(4000);
            FakeWebsite.send(exchange, 200, "text/html", "<html/>".getBytes());
        });

        long start = System.nanoTime();
        assertThatThrownBy(() -> fetcher.page(site.url("/slow"), Deadline.after(Duration.ofMillis(800))))
                .isInstanceOfSatisfying(FetchException.class,
                        e -> assertThat(e.outcome()).isEqualTo(EnrichmentOutcome.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void aSpentBudgetSendsNoRequest() {
        site.html("/", "<html/>");
        Deadline spent = Deadline.after(Duration.ZERO);

        assertThatThrownBy(() -> fetcher.page(site.url("/"), spent)).isInstanceOfSatisfying(FetchException.class,
                e -> assertThat(e.outcome()).isEqualTo(EnrichmentOutcome.TIMEOUT));
        assertThat(site.requests()).isEmpty();
    }

    // --- image check (R4, T7) --------------------------------------------------------------------------------------

    @Test
    void anImageIsAcceptedOnlyOn200WithAnImageContentType() {
        site.image("/ok.jpg")
                .on("/html.jpg", exchange -> FakeWebsite.send(exchange, 200, "text/html", "<html/>".getBytes()))
                .on("/204.jpg", exchange -> FakeWebsite.send(exchange, 204, "image/jpeg", new byte[0]))
                .redirect("/moved.jpg", "/ok.jpg");

        assertThat(fetcher.isImage(site.url("/ok.jpg"), fiveSeconds())).isTrue();
        assertThat(fetcher.isImage(site.url("/moved.jpg"), fiveSeconds())).isTrue();
        assertThat(fetcher.isImage(site.url("/missing.jpg"), fiveSeconds())).isFalse();
        assertThat(fetcher.isImage(site.url("/html.jpg"), fiveSeconds())).isFalse();
        assertThat(fetcher.isImage(site.url("/204.jpg"), fiveSeconds())).isFalse();
    }

    @Test
    void theImageCheckDoesNotReadTheWholeImage() {
        AtomicInteger written = new AtomicInteger();
        site.on("/huge.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[64 * 1024];
            for (int i = 0; i < 1000; i++) { // 64 MB if fully read
                exchange.getResponseBody().write(chunk);
                written.addAndGet(chunk.length);
            }
        });

        long start = System.nanoTime();
        assertThat(fetcher.isImage(site.url("/huge.jpg"), fiveSeconds())).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void theImageCheckAppliesTheSameAddressRules() {
        SafeWebFetcher production = strict(TestCardEnrichmentProperties.allowingPort(site.port()), "10.0.0.1");
        site.image("/ok.jpg");

        assertThat(production.isImage(site.url("/ok.jpg"), fiveSeconds())).isFalse();
        assertThat(fetcher.isImage(URI.create("ftp://x.test/a.jpg"), fiveSeconds())).isFalse();
        site.redirect("/to-private.jpg", "http://192.168.0.1/a.jpg");
        assertThat(fetcher.isImage(site.url("/to-private.jpg"), fiveSeconds())).isFalse();
        assertThat(site.paths()).containsExactly("/to-private.jpg");
    }

    @Test
    void outcomesAreTheDataModelList() {
        assertThat(List.of(EnrichmentOutcome.values())).extracting(Enum::name).containsExactly("SHARING_IMAGE",
                "LARGEST_PAGE_IMAGE", "NO_IMAGE", "NO_DISPLAYABLE_IMAGE", "UNREACHABLE", "BLOCKED_ADDRESS", "NOT_HTML",
                "TIMEOUT", "UNSPLASH_CUISINE_PHOTO", "NO_CUISINE_PHOTO", "UNSPLASH_UNAVAILABLE");
    }
}
