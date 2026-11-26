package hr.emocio.recoresto.search.enrichment;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Reads pages and checks images on websites chosen by third parties, safely (spec 007 research R4, R5, R6):
 * <ul>
 * <li>before every connection (first request, every redirect hop, image check) the address must pass
 * {@link WebsiteAddress} (http/https, host, no user-info, ports of PO-8) and every address its host resolves to must be
 * public: loopback, any-local, private, carrier-grade NAT, link-local (EC2 metadata), unique-local IPv6, multicast and
 * the IPv6 forms that carry an IPv4 address (mapped, compatible, NAT64 {@code 64:ff9b::/96}, 6to4 {@code 2002::/16})
 * are refused ({@code BLOCKED_ADDRESS});</li>
 * <li>the connection itself resolves the host through the same rule and uses only the addresses that passed it, so an
 * answer that changes after the check (DNS rebinding) never reaches a non-public address (PR #15 review);</li>
 * <li>redirects are followed by hand, at most {@code card-enrichment.max-redirects} (PO-7);</li>
 * <li>a page is read as a stream and cut at {@code card-enrichment.max-page-bytes} (PO-1); an image body is never
 * read;</li>
 * <li>each request gets the remaining budget of the enrichment as timeout and is aborted when the budget is spent,
 * whatever the website sends (research R6);</li>
 * <li>{@code User-Agent} from {@code card-enrichment.user-agent}; {@code robots.txt} is never read (PO-6).</li>
 * </ul>
 */
@Component
public class SafeWebFetcher implements AutoCloseable {

    /** Resolves a host name to all its addresses. */
    @FunctionalInterface
    public interface Resolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    /** The resolution made to connect found a non-public address. */
    private static final class BlockedAddressException extends UnknownHostException {
        BlockedAddressException() {
            super("Non-public address");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SafeWebFetcher.class);

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final String HTML = "text/html";
    private static final String IMAGE = "image/*";

    /** The answer to one request, read inside the exchange so that the connection is closed right after. */
    private record Answer(URI url, int status, String location, MediaType type, byte[] body) {
    }

    private final CloseableHttpClient httpClient;
    private final ScheduledExecutorService aborter;
    private final CardEnrichmentProperties properties;
    private final Resolver resolver;
    private final Predicate<InetAddress> allowedAddress;

    @Autowired
    public SafeWebFetcher(CardEnrichmentProperties properties) {
        this(properties, SafeWebFetcher::resolve, SafeWebFetcher::isPublic);
    }

    /** Tests replace the resolver or the address rule (the production rule never reaches a loopback fake site). */
    SafeWebFetcher(CardEnrichmentProperties properties, Resolver resolver, Predicate<InetAddress> allowedAddress) {
        this.properties = properties;
        this.resolver = resolver;
        this.allowedAddress = allowedAddress;
        Timeout budget = Timeout.ofSeconds(properties.budgetSeconds());
        this.httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new CheckedDnsResolver())
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(budget)
                                .setSocketTimeout(budget).build())
                        .build())
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                .disableContentCompression().disableAuthCaching().disableDefaultUserAgent().build();
        this.aborter = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "website-request-aborter");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The resolution used to connect: the same rule as the check, so only checked addresses are ever contacted. */
    private final class CheckedDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            List<InetAddress> resolved = resolver.resolve(host);
            if (resolved == null || resolved.isEmpty()) {
                throw new UnknownHostException("Unknown host");
            }
            if (!resolved.stream().allMatch(allowedAddress)) {
                throw new BlockedAddressException();
            }
            return resolved.toArray(InetAddress[]::new);
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }

    @Override
    public void close() {
        httpClient.close(CloseMode.IMMEDIATE);
        aborter.shutdownNow();
    }

    static List<InetAddress> resolve(String host) throws UnknownHostException {
        return Arrays.asList(InetAddress.getAllByName(host));
    }

    /** Whether an address may be contacted (research R5). */
    public static boolean isPublic(InetAddress address) {
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            if (embedsIpv4(bytes)) {
                return isPublic(ipv4(bytes, 12));
            }
            if (isNat64(bytes)) { // 64:ff9b::/96, the IPv4 address in the last 4 bytes
                return isPublic(ipv4(bytes, 12));
            }
            if (bytes[0] == 0x20 && bytes[1] == 0x02) { // 2002::/16 6to4, the IPv4 address in bytes 2 to 5
                return isPublic(ipv4(bytes, 2));
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

    private static InetAddress ipv4(byte[] bytes, int from) {
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(bytes, from, from + 4));
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
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

    /** The NAT64 well-known prefix {@code 64:ff9b::/96} (RFC 6052). */
    private static boolean isNat64(byte[] bytes) {
        if (bytes[0] != 0 || bytes[1] != 0x64 || bytes[2] != (byte) 0xff || bytes[3] != (byte) 0x9b) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
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

    /**
     * One request, aborted when the budget is spent. The connection is always discarded afterwards, so an image or the
     * rest of a long page is never read.
     */
    private Answer send(URI url, Deadline deadline, boolean page) throws FetchException {
        Duration remaining = deadline.remaining();
        if (remaining.compareTo(Duration.ZERO) <= 0) {
            throw new FetchException(EnrichmentOutcome.TIMEOUT, "Budget spent");
        }
        HttpGet request = new HttpGet(url);
        request.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.of(remaining)).build());
        request.setHeader(HttpHeaders.USER_AGENT, properties.userAgent());
        request.setHeader(HttpHeaders.ACCEPT, page ? HTML : IMAGE);
        ScheduledFuture<?> abort = aborter.schedule(request::cancel, remaining.toNanos(), TimeUnit.NANOSECONDS);
        ClassicHttpResponse response = null;
        try {
            response = httpClient.executeOpen(null, request, null);
            int status = response.getCode();
            MediaType type = contentType(response.getFirstHeader(HttpHeaders.CONTENT_TYPE));
            byte[] body = page && status / 100 == 2 && isHtml(type) ? read(response.getEntity()) : null;
            Header location = response.getFirstHeader(HttpHeaders.LOCATION);
            return new Answer(url, status, location == null ? null : location.getValue(), type, body);
        } catch (IOException e) {
            if (hasCause(e, BlockedAddressException.class)) {
                throw new FetchException(EnrichmentOutcome.BLOCKED_ADDRESS, "Non-public address at connection");
            }
            if (deadline.isSpent() || Thread.currentThread().isInterrupted() || request.isCancelled()) {
                throw new FetchException(EnrichmentOutcome.TIMEOUT, "Budget spent during the request");
            }
            throw new FetchException(EnrichmentOutcome.UNREACHABLE, rootCause(e));
        } finally {
            abort.cancel(false);
            request.cancel(); // discards the connection, so the rest of the answer is never read
            closeQuietly(response);
        }
    }

    private static void closeQuietly(ClassicHttpResponse response) {
        if (response == null) {
            return;
        }
        try {
            response.close();
        } catch (IOException | RuntimeException alreadyDiscarded) {
            // The connection was discarded by the cancel: nothing left to release.
        }
    }

    private byte[] read(HttpEntity entity) throws IOException {
        if (entity == null) {
            return new byte[0];
        }
        InputStream content = entity.getContent();
        return content.readNBytes(properties.maxPageBytes());
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static MediaType contentType(Header header) {
        if (header == null || header.getValue() == null || header.getValue().isBlank()) {
            return null;
        }
        try {
            return MediaType.parseMediaType(header.getValue());
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
