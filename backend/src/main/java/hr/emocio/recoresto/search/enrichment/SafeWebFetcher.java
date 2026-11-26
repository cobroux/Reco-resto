package hr.emocio.recoresto.search.enrichment;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads pages and checks images on websites chosen by third parties, safely (spec 007 research R4, R5, R6):
 * <ul>
 * <li>before every connection (first request, every redirect hop, image check) the address must pass
 * {@link WebsiteAddress} (http/https, host, no user-info, ports of PO-8) and every address its host resolves to must be
 * public: loopback, any-local, private, carrier-grade NAT, link-local (EC2 metadata), unique-local IPv6, multicast and
 * their IPv4-mapped forms are refused ({@code BLOCKED_ADDRESS});</li>
 * <li>redirects are followed by hand, at most {@code card-enrichment.max-redirects} (PO-7);</li>
 * <li>a page is read as a stream and cut at {@code card-enrichment.max-page-bytes} (PO-1); an image body is never
 * read;</li>
 * <li>each request gets the remaining budget of the enrichment as timeout, and an interrupted thread abandons the
 * request (research R6);</li>
 * <li>{@code User-Agent} from {@code card-enrichment.user-agent}; {@code robots.txt} is never read (PO-6).</li>
 * </ul>
 * Residual risk accepted for the POC: DNS rebinding between the check and the connection (research R5).
 */
@Component
public class SafeWebFetcher {

    /** Resolves a host name to all its addresses. */
    @FunctionalInterface
    public interface Resolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    private static final Logger log = LoggerFactory.getLogger(SafeWebFetcher.class);

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final String HTML = "text/html";
    private static final String IMAGE = "image/*";

    /** The answer to one request, read inside the exchange so that the connection is closed right after. */
    private record Answer(URI url, int status, String location, MediaType type, byte[] body) {
    }

    private final RestClient.Builder builder;
    private final HttpClient httpClient;
    private final CardEnrichmentProperties properties;
    private final Resolver resolver;
    private final Predicate<InetAddress> allowedAddress;

    @Autowired
    public SafeWebFetcher(RestClient.Builder builder, CardEnrichmentProperties properties) {
        this(builder, properties, SafeWebFetcher::resolve, SafeWebFetcher::isPublic);
    }

    /** Tests replace the resolver or the address rule (the production rule never reaches a loopback fake site). */
    SafeWebFetcher(RestClient.Builder builder, CardEnrichmentProperties properties, Resolver resolver,
            Predicate<InetAddress> allowedAddress) {
        this.builder = builder;
        this.properties = properties;
        this.resolver = resolver;
        this.allowedAddress = allowedAddress;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(properties.budgetSeconds())).build();
    }

    static List<InetAddress> resolve(String host) throws UnknownHostException {
        return Arrays.asList(InetAddress.getAllByName(host));
    }

    /** Whether an address may be contacted (research R5). */
    public static boolean isPublic(InetAddress address) {
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            if (embedsIpv4(bytes)) {
                try {
                    return isPublic(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
                } catch (UnknownHostException impossible) {
                    return false;
                }
            }
            if ((bytes[0] & 0xfe) == 0xfc) { // fc00::/7 unique local
                return false;
            }
        }
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            int first = address.getAddress()[0] & 0xff;
            int second = address.getAddress()[1] & 0xff;
            if (first == 0) { // 0.0.0.0/8 "this network"
                return false;
            }
            if (first == 100 && (second & 0xc0) == 64) { // 100.64.0.0/10 carrier-grade NAT
                return false;
            }
        }
        return true;
    }

    /** IPv4-mapped ({@code ::ffff:a.b.c.d}) and IPv4-compatible ({@code ::a.b.c.d}) IPv6 addresses. */
    private static boolean embedsIpv4(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) || (bytes[10] == 0 && bytes[11] == 0);
    }

    /**
     * The website page at this address, after redirects, cut at the size limit.
     *
     * @throws FetchException with {@code BLOCKED_ADDRESS}, {@code UNREACHABLE}, {@code NOT_HTML} or {@code TIMEOUT}
     */
    public FetchedPage page(URI url, Deadline deadline) throws FetchException {
        Answer answer = follow(url, deadline, true);
        if (answer.status() / 100 != 2) {
            throw new FetchException(EnrichmentOutcome.UNREACHABLE, "HTTP " + answer.status());
        }
        if (!isHtml(answer.type())) {
            throw new FetchException(EnrichmentOutcome.NOT_HTML, "Content-Type " + answer.type());
        }
        String charset = answer.type().getParameter("charset");
        boolean known = charset != null && Charset.isSupported(charset.replace("\"", ""));
        return new FetchedPage(answer.url(), answer.body(), known ? charset.replace("\"", "") : null);
    }

    /**
     * Whether the address answers as an image: HTTP 200 with a {@code Content-Type} starting with {@code image/}, after
     * redirects, with the same address rules (research R4). The body is not read.
     */
    public boolean isImage(URI url, Deadline deadline) {
        try {
            Answer answer = follow(url, deadline, false);
            return answer.status() == 200 && answer.type() != null && answer.type().getType().equals("image");
        } catch (FetchException e) {
            log.debug("Image check failed on {}: {}", url.getHost(), e.outcome());
            return false;
        }
    }

    /** Follows the redirects by hand, each hop checked before connecting. */
    private Answer follow(URI start, Deadline deadline, boolean page) throws FetchException {
        URI current = start;
        for (int redirects = 0;; redirects++) {
            check(current);
            Answer answer = send(current, deadline, page);
            if (!REDIRECT_STATUSES.contains(answer.status()) || answer.location() == null) {
                return answer;
            }
            if (redirects >= properties.maxRedirects()) {
                throw new FetchException(EnrichmentOutcome.UNREACHABLE, "Too many redirects");
            }
            current = next(current, answer.location());
        }
    }

    private static URI next(URI current, String location) throws FetchException {
        try {
            URI base = current.getRawPath() == null || current.getRawPath().isEmpty() ? current.resolve("/") : current;
            return base.resolve(new URI(location.trim()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new FetchException(EnrichmentOutcome.UNREACHABLE, "Unreadable redirect");
        }
    }

    /** The address rule and the public-address rule on every resolved address (research R5, PO-8). */
    private void check(URI url) throws FetchException {
        WebsiteAddress address = WebsiteAddress.of(url, properties.allowedPorts()).orElseThrow(
                () -> new FetchException(EnrichmentOutcome.BLOCKED_ADDRESS, "Address refused by the website rule"));
        List<InetAddress> resolved;
        try {
            resolved = resolver.resolve(address.host());
        } catch (UnknownHostException e) {
            throw new FetchException(EnrichmentOutcome.UNREACHABLE, "Unknown host");
        }
        if (resolved == null || resolved.isEmpty()) {
            throw new FetchException(EnrichmentOutcome.UNREACHABLE, "Unknown host");
        }
        if (!resolved.stream().allMatch(allowedAddress)) {
            throw new FetchException(EnrichmentOutcome.BLOCKED_ADDRESS, "Non-public address");
        }
    }

    private Answer send(URI url, Deadline deadline, boolean page) throws FetchException {
        Duration remaining = deadline.remaining();
        if (remaining.compareTo(Duration.ZERO) <= 0) {
            throw new FetchException(EnrichmentOutcome.TIMEOUT, "Budget spent");
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(remaining);
        RestClient client = builder.clone().requestFactory(factory).build();
        try {
            return client.get().uri(url).header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .header(HttpHeaders.ACCEPT, page ? HTML : IMAGE)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        MediaType type = contentType(response.getHeaders());
                        byte[] body = page && status / 100 == 2 && isHtml(type)
                                ? response.getBody().readNBytes(properties.maxPageBytes()) : null;
                        return new Answer(url, status, response.getHeaders().getFirst(HttpHeaders.LOCATION), type,
                                body);
                    });
        } catch (RestClientException e) {
            if (deadline.isSpent() || Thread.currentThread().isInterrupted()) {
                throw new FetchException(EnrichmentOutcome.TIMEOUT, "Budget spent during the request");
            }
            throw new FetchException(EnrichmentOutcome.UNREACHABLE, rootCause(e));
        }
    }

    private static MediaType contentType(HttpHeaders headers) {
        try {
            return headers.getContentType();
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    private static boolean isHtml(MediaType type) {
        return type != null && (MediaType.TEXT_HTML.equalsTypeAndSubtype(type)
                || MediaType.APPLICATION_XHTML_XML.equalsTypeAndSubtype(type));
    }

    /** The class name of the first I/O cause, for the logs (never a message: it may carry an address). */
    static String rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && !(cause instanceof IOException)) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
