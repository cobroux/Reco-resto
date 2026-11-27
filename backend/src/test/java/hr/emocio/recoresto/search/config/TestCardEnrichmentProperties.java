package hr.emocio.recoresto.search.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The decided {@code card-enrichment.*} values (asserted against {@code application.yml} by
 * {@link CardEnrichmentPropertiesTest}) for tests that build the enrichment classes by hand.
 */
public final class TestCardEnrichmentProperties {

    private TestCardEnrichmentProperties() {
    }

    public static CardEnrichmentProperties defaults() {
        return new CardEnrichmentProperties(5, 1_048_576, 100, 5, List.of(80, 443),
                "RecoResto/0.1 (proof of concept)", 8);
    }

    /** The defaults plus one more allowed port (the port of a local fake server). */
    public static CardEnrichmentProperties allowingPort(int port) {
        return withPortsAndExecutor(port, 8);
    }

    /** The defaults plus one more allowed port and another executor size. */
    public static CardEnrichmentProperties withPortsAndExecutor(int port, int executorSize) {
        CardEnrichmentProperties d = defaults();
        List<Integer> ports = new ArrayList<>(d.allowedPorts());
        ports.add(port);
        return new CardEnrichmentProperties(d.budgetSeconds(), d.maxPageBytes(), d.minImageSizePx(),
                d.maxRedirects(), ports, d.userAgent(), executorSize);
    }
}
