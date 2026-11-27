package hr.emocio.recoresto.search.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 007 T3, FR-002; data-model.md "Website address"; research R3; PO-8. */
class WebsiteAddressTest {

    private static final List<Integer> PORTS = List.of(80, 443);

    private static boolean valid(String value) {
        return WebsiteAddress.parse(value, PORTS).isPresent();
    }

    @Test
    void absoluteHttpOrHttpsAddressesWithAHostAreValid() {
        assertThat(valid("https://x.fr/")).isTrue();
        assertThat(valid("HTTP://x.fr")).isTrue();
        assertThat(valid("http://www.example-restaurant.fr/menu?x=1#top")).isTrue();
        assertThat(valid("https://x.fr:443/")).isTrue();
        assertThat(valid("http://x.fr:80/")).isTrue();
    }

    @Test
    void theParsedAddressKeepsTheGivenValue() {
        assertThat(WebsiteAddress.parse("  https://x.fr/a  ", PORTS)).get()
                .satisfies(address -> {
                    assertThat(address.uri().toString()).isEqualTo("https://x.fr/a");
                    assertThat(address.host()).isEqualTo("x.fr");
                    assertThat(address.port()).isEqualTo(443);
                });
        assertThat(WebsiteAddress.parse("http://x.fr", PORTS)).get().extracting(WebsiteAddress::port).isEqualTo(80);
    }

    @Test
    void otherSchemesAreInvalid() {
        assertThat(valid("ftp://example.com")).isFalse();
        assertThat(valid("file:///etc/passwd")).isFalse();
        assertThat(valid("javascript:alert(1)")).isFalse();
        assertThat(valid("mailto:a@x.fr")).isFalse();
    }

    @Test
    void relativeOrUnparseableOrMissingValuesAreInvalid() {
        assertThat(valid("monresto")).isFalse();
        assertThat(valid("/menu")).isFalse();
        assertThat(valid("//x.fr/")).isFalse();
        assertThat(valid("http://exa mple.fr/")).isFalse();
        assertThat(valid("")).isFalse();
        assertThat(valid("   ")).isFalse();
        assertThat(valid(null)).isFalse();
    }

    @Test
    void anAddressWithoutHostIsInvalid() {
        assertThat(valid("http:///menu")).isFalse();
        assertThat(valid("https:")).isFalse();
    }

    @Test
    void anAddressWithUserInfoIsInvalid() {
        assertThat(valid("https://user:pass@x.fr/")).isFalse();
        assertThat(valid("https://user@x.fr/")).isFalse();
    }

    /** PO-8: ports 80 and 443 only. */
    @Test
    void anExplicitPortOtherThanTheAllowedOnesIsInvalid() {
        assertThat(valid("http://x.fr:8080/")).isFalse();
        assertThat(valid("https://x.fr:22/")).isFalse();
        assertThat(WebsiteAddress.parse("http://x.fr/", List.of(443))).isEmpty();
    }
}
