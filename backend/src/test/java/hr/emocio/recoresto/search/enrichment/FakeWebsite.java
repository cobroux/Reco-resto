package hr.emocio.recoresto.search.enrichment;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A local fake website (JDK {@code HttpServer} on the loopback address) for the enrichment tests: no real site is
 * ever contacted; also the local Unsplash stub. Every request is recorded with its path, query and headers. Content is invented, NOT real data.
 */
final class FakeWebsite implements AutoCloseable {

    /** One recorded request; {@code query} is the decoded query string, null when there is none. */
    record Request(String path, String query, Headers headers) {
    }

    /** What a path answers. */
    @FunctionalInterface
    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final HttpServer server;
    private final Map<String, Handler> routes = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    FakeWebsite() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery(),
                    exchange.getRequestHeaders()));
            Handler handler = routes.get(exchange.getRequestURI().getPath());
            try {
                if (handler == null) {
                    send(exchange, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
                } else {
                    handler.handle(exchange);
                }
            } catch (IOException clientGone) {
                // The client closed the connection (size cut, deadline): nothing to answer.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    /** Absolute address of a path on this site, with the loopback literal as host. */
    URI url(String path) {
        return URI.create("http://127.0.0.1:" + port() + path);
    }

    FakeWebsite on(String path, Handler handler) {
        routes.put(path, handler);
        return this;
    }

    FakeWebsite html(String path, String html) {
        return on(path, exchange -> send(exchange, 200, "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8)));
    }

    FakeWebsite image(String path) {
        return on(path, exchange -> send(exchange, 200, "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8}));
    }

    FakeWebsite redirect(String path, String location) {
        return on(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
        });
    }

    List<Request> requests() {
        return requests;
    }

    List<String> paths() {
        return requests.stream().map(Request::path).toList();
    }

    static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
