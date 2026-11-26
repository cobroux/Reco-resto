package hr.emocio.recoresto.search.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec 008 T16, T17, T8; D13. The Google request itself (Text Search, mask, key header) is checked in
 * {@code GooglePlacesClientTest}; here the client is a fake answering recorded Text Search places.
 */
class PlaceLocatorTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final GooglePlacesClient client = mock(GooglePlacesClient.class);
    private final PlaceLocator locator = new PlaceLocator(client);

    private void googleFinds(String name, String place) {
        when(client.locate(name)).thenReturn(place == null ? Optional.empty() : Optional.of(json.readTree(place)));
    }

    private static String place(double latitude, double longitude, String country) {
        return "{\"location\":{\"latitude\":" + latitude + ",\"longitude\":" + longitude + "},"
                + "\"addressComponents\":[{\"longText\":\"Paris\",\"shortText\":\"Paris\",\"types\":[\"locality\","
                + "\"political\"]},{\"longText\":\"x\",\"shortText\":\"" + country + "\",\"types\":[\"country\","
                + "\"political\"]}]}";
    }

    @Test
    void aPlaceIsLocatedByGoogle() {
        googleFinds("Opéra, Paris", place(48.8708, 2.3322, "FR"));

        assertThat(locator.locate("Opéra, Paris")).isEqualTo(new Position(48.8708, 2.3322));
    }

    /** D13 (3). */
    @Test
    void aPlaceGoogleDoesNotFindIsAPlaceRequired() {
        googleFinds("Nulle part", null);

        assertCode(() -> locator.locate("Nulle part"), SearchErrorCode.PLACE_REQUIRED);
    }

    @Test
    void aResultWithoutValidLocationIsAPlaceRequired() {
        googleFinds("Sans position", "{\"addressComponents\":[]}");
        googleFinds("Hors limites", place(148.0, 2.3, "FR"));

        assertCode(() -> locator.locate("Sans position"), SearchErrorCode.PLACE_REQUIRED);
        assertCode(() -> locator.locate("Hors limites"), SearchErrorCode.PLACE_REQUIRED);
    }

    /** T8, D13: a point located outside France. */
    @Test
    void aPlaceLocatedOutsideFranceIsOutsideFrance() {
        googleFinds("Colisée", place(41.8902, 12.4922, "IT"));

        assertCode(() -> locator.locate("Colisée"), SearchErrorCode.OUTSIDE_FRANCE);
    }

    @Test
    void aBlankNameIsAPlaceRequiredWithoutCallingGoogle() {
        assertCode(() -> locator.locate(" "), SearchErrorCode.PLACE_REQUIRED);
        assertCode(() -> locator.locate(null), SearchErrorCode.PLACE_REQUIRED);
        verifyNoInteractions(client);
    }

    @Test
    void aGoogleFailureIsNotHidden() {
        when(client.locate("Opéra")).thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "down"));

        assertCode(() -> locator.locate("Opéra"), SearchErrorCode.SERVICE_UNAVAILABLE);
    }

    private static void assertCode(Runnable action, SearchErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(SearchException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }
}
