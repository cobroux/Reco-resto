package hr.emocio.recoresto.search.google;

import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.config.TestCardEnrichmentProperties;
import hr.emocio.recoresto.search.restaurant.Attribution;
import hr.emocio.recoresto.search.restaurant.DataProvider;
import hr.emocio.recoresto.search.restaurant.OpeningPeriod;
import hr.emocio.recoresto.search.restaurant.Restaurant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Spec 008 T1, T10, T23; data-model.md "Restaurant" (core fields). */
class GooglePlaceMapperTest {

    private static final Position OPERA = new Position(48.8708, 2.3322);
    private static final Position CHATELET = new Position(48.8584, 2.3470);

    private final JsonMapper json = JsonMapper.builder().build();
    private final GooglePlaceMapper mapper = new GooglePlaceMapper(TestCardEnrichmentProperties.defaults());

    private static final String COMPLETE = """
            {"id":"place-1",
             "displayName":{"text":"Chez Test","languageCode":"fr"},
             "location":{"latitude":48.8715,"longitude":2.3305},
             "shortFormattedAddress":"1 Rue Exemple, Paris",
             "types":["italian_restaurant","restaurant","food"],
             "primaryType":"italian_restaurant",
             "primaryTypeDisplayName":{"text":"Restaurant italien","languageCode":"fr"},
             "businessStatus":"OPERATIONAL",
             "servesVegetarianFood":true,
             "regularOpeningHours":{"periods":[{"open":{"day":1,"hour":12,"minute":0},
                                                "close":{"day":1,"hour":14,"minute":0}}]}}""";

    private Optional<GooglePlace> map(String place, Position... points) {
        return mapper.map(json.readTree(place), List.of(points));
    }

    private Restaurant restaurant(String place, Position... points) {
        return map(place, points).orElseThrow().restaurant();
    }

    @Test
    void aCompletePlaceGivesItsCoreFields() {
        GooglePlace place = map(COMPLETE, OPERA).orElseThrow();
        Restaurant restaurant = place.restaurant();

        assertThat(restaurant.name()).isEqualTo("Chez Test");
        assertThat(restaurant.location()).isEqualTo(new Position(48.8715, 2.3305));
        assertThat(restaurant.distanceMeters()).isEqualTo((int) Math.round(OPERA.distanceMetersTo(
                new Position(48.8715, 2.3305))));
        assertThat(restaurant.cuisine()).isEqualTo("Restaurant italien"); // D21 (1)
        assertThat(restaurant.address()).isEqualTo("1 Rue Exemple, Paris");
        assertThat(restaurant.openingHours()).containsOnlyKeys("MONDAY");
        assertThat(restaurant.openingHours().get("MONDAY")).containsExactly(new OpeningPeriod("12:00", "14:00"));
        assertThat(restaurant.placeId()).isEqualTo("place-1"); // T1
        assertThat(place.id()).isEqualTo("place-1");
        assertThat(place.types()).containsExactly("italian_restaurant", "restaurant", "food");
        assertThat(place.primaryType()).isEqualTo("italian_restaurant");
        assertThat(place.businessStatus()).isEqualTo("OPERATIONAL");
        assertThat(place.servesVegetarianFood()).isTrue();
    }

    @Test
    void keywordsSignatureDishAndMenuAreAlwaysAbsent() {
        Restaurant restaurant = restaurant(COMPLETE, OPERA);

        assertThat(restaurant.keywords()).isNull();
        assertThat(restaurant.signatureDish()).isNull();
        assertThat(restaurant.menu()).isNull();
    }

    @Test
    void aPlaceWithoutNameIsDropped() {
        assertThat(map("{\"id\":\"x\",\"location\":{\"latitude\":48.87,\"longitude\":2.33}}", OPERA)).isEmpty();
        assertThat(map("{\"id\":\"x\",\"displayName\":{\"text\":\"  \"}}", OPERA)).isEmpty();
    }

    @Test
    void invalidOrMissingCoordinatesGiveNoLocationAndNoDistance() {
        Restaurant invalid = restaurant("{\"displayName\":{\"text\":\"A\"},\"location\":{\"latitude\":95.0,"
                + "\"longitude\":2.3}}", OPERA);
        Restaurant missing = restaurant("{\"displayName\":{\"text\":\"B\"}}", OPERA);

        assertThat(invalid.location()).isNull();
        assertThat(invalid.distanceMeters()).isNull();
        assertThat(missing.location()).isNull();
        assertThat(missing.distanceMeters()).isNull();
    }

    @Test
    void theDistanceIsTheOneToTheNearestSearchPointRounded() {
        Position nearChatelet = new Position(48.8590, 2.3475);

        Restaurant restaurant = restaurant("{\"displayName\":{\"text\":\"A\"},\"location\":{\"latitude\":48.8590,"
                + "\"longitude\":2.3475}}", OPERA, CHATELET);

        assertThat(restaurant.distanceMeters()).isEqualTo((int) Math.round(CHATELET.distanceMetersTo(nearChatelet)));
    }

    /** D21 (2): the short address, without ", France". */
    @Test
    void theAddressIsTheShortAddressWithoutFrance() {
        assertThat(restaurant("{\"displayName\":{\"text\":\"A\"},\"shortFormattedAddress\":\"2 Rue X, Paris, "
                + "France\"}", OPERA).address()).isEqualTo("2 Rue X, Paris");
    }

    /** T10: a place without address keeps no address (then excluded by the filters). */
    @Test
    void aMissingAddressStaysAbsent() {
        assertThat(restaurant("{\"displayName\":{\"text\":\"A\"}}", OPERA).address()).isNull();
        assertThat(restaurant("{\"displayName\":{\"text\":\"A\"},\"shortFormattedAddress\":\" \"}", OPERA).address())
                .isNull();
    }

    /** T23: no hours from Google, no hours in the restaurant. */
    @Test
    void missingOpeningHoursStayAbsent() {
        assertThat(restaurant("{\"displayName\":{\"text\":\"A\"}}", OPERA).openingHours()).isNull();
    }

    @Test
    void missingOptionalGoogleFieldsStayAbsent() {
        GooglePlace place = map("{\"displayName\":{\"text\":\"A\"}}", OPERA).orElseThrow();

        assertThat(place.id()).isNull();
        assertThat(place.restaurant().placeId()).isNull();
        assertThat(place.restaurant().cuisine()).isNull();
        assertThat(place.types()).isEmpty();
        assertThat(place.businessStatus()).isNull();
        assertThat(place.servesVegetarianFood()).isNull();
    }

    // --- Card fields (T034: T21, T22, T24, T25, T29) ---

    private static String withField(String field) {
        return "{\"id\":\"place-1\",\"displayName\":{\"text\":\"A\"}" + (field.isEmpty() ? "" : "," + field) + "}";
    }

    /** D5. */
    @Test
    void thePriceLevelIsTheEuroFormatOfFeature003() {
        assertThat(restaurant(withField("\"priceLevel\":\"PRICE_LEVEL_INEXPENSIVE\""), OPERA).priceLevel())
                .isEqualTo("€");
        assertThat(restaurant(withField("\"priceLevel\":\"PRICE_LEVEL_MODERATE\""), OPERA).priceLevel())
                .isEqualTo("€€");
        assertThat(restaurant(withField("\"priceLevel\":\"PRICE_LEVEL_EXPENSIVE\""), OPERA).priceLevel())
                .isEqualTo("€€€");
        assertThat(restaurant(withField("\"priceLevel\":\"PRICE_LEVEL_VERY_EXPENSIVE\""), OPERA).priceLevel())
                .isEqualTo("€€€€");
    }

    /** D5, T24: free, unspecified, unknown or absent: no price. */
    @Test
    void freeUnspecifiedOrMissingPriceLevelGivesNoPrice() {
        assertThat(restaurant(withField("\"priceLevel\":\"PRICE_LEVEL_FREE\""), OPERA).priceLevel()).isNull();
        assertThat(restaurant(withField("\"priceLevel\":\"PRICE_LEVEL_UNSPECIFIED\""), OPERA).priceLevel()).isNull();
        assertThat(restaurant(withField("\"priceLevel\":\"SOMETHING_NEW\""), OPERA).priceLevel()).isNull();
        assertThat(restaurant(withField(""), OPERA).priceLevel()).isNull();
    }

    /** Spec 008 T21, T25; spec 007 T1, T2. */
    @Test
    void theWebsiteIsGivenAsIsAndAbsentWhenMissing() {
        assertThat(restaurant(withField("\"websiteUri\":\"https://example.test/a\""), OPERA).website())
                .isEqualTo("https://example.test/a");
        assertThat(restaurant(withField("\"websiteUri\":\"http://example.test\""), OPERA).website())
                .isEqualTo("http://example.test");
        assertThat(restaurant(withField(""), OPERA).website()).isNull();
    }

    /** Spec 007 T3, TC-3, research R3: an address breaking the website rule is not given. */
    @Test
    void anInvalidWebsiteIsNotGiven() {
        assertThat(restaurant(withField("\"websiteUri\":\"ftp://example.test/\""), OPERA).website()).isNull();
        assertThat(restaurant(withField("\"websiteUri\":\"monresto\""), OPERA).website()).isNull();
        assertThat(restaurant(withField("\"websiteUri\":\"https://user:pw@example.test/\""), OPERA).website())
                .isNull();
        assertThat(restaurant(withField("\"websiteUri\":\"http://example.test:8080/\""), OPERA).website()).isNull();
    }

    /** D21 (3): national format. */
    @Test
    void thePhoneIsTheNationalNumber() {
        assertThat(restaurant(withField("\"nationalPhoneNumber\":\"01 23 45 67 89\""), OPERA).phone())
                .isEqualTo("01 23 45 67 89");
        assertThat(restaurant(withField(""), OPERA).phone()).isNull();
    }

    private static final String PHOTOS = """
            "photos":[{"name":"places/place-1/photos/AbC_12-x","widthPx":1200,"heightPx":800,
                       "authorAttributions":[{"displayName":"Author One","uri":"https://example.test/author",
                                              "photoUri":"https://example.test/author.png"}],
                       "googleMapsUri":"https://example.test/photo-on-maps"},
                      {"name":"places/place-1/photos/second","authorAttributions":[]}]""";

    /** D16 (a), T29: a backend address without key and without Google host, from the first photo. */
    @Test
    void thePhotoAddressIsTheBackendAddressOfTheFirstPhoto() {
        Restaurant restaurant = restaurant(withField(PHOTOS), OPERA);

        assertThat(restaurant.photoUrl()).isEqualTo("/api/photos/place-1/AbC_12-x");
        assertThat(restaurant.photoUrl()).doesNotContain("key").doesNotContain("googleapis");
        assertThat(restaurant.photoAttributions()).containsExactly(new Attribution("Author One",
                "https://example.test/author"));
        assertThat(restaurant.photoSourceUri()).isEqualTo("https://example.test/photo-on-maps");
    }

    /** T22. */
    @Test
    void withoutPhotoThereIsNoPhotoAddressNorPhotoAttribution() {
        Restaurant restaurant = restaurant(withField(""), OPERA);

        assertThat(restaurant.photoUrl()).isNull();
        assertThat(restaurant.photoAttributions()).isNull();
        assertThat(restaurant.photoSourceUri()).isNull();
    }

    @Test
    void anEmptyAuthorListGivesNoPhotoAttribution() {
        Restaurant restaurant = restaurant(withField("\"photos\":[{\"name\":\"places/place-1/photos/p\","
                + "\"authorAttributions\":[]}]"), OPERA);

        assertThat(restaurant.photoUrl()).isEqualTo("/api/photos/place-1/p");
        assertThat(restaurant.photoAttributions()).isNull();
    }

    /** The photo endpoint only accepts identifier characters (research R5): another name gives no address. */
    @Test
    void aPhotoNameThatCannotBeServedGivesNoPhotoAddress() {
        assertThat(restaurant(withField("\"photos\":[{\"name\":\"places/place-1/photos/a.b\"}]"), OPERA)
                .photoUrl()).isNull();
        assertThat(restaurant(withField("\"photos\":[{\"name\":\"something/else\"}]"), OPERA).photoUrl())
                .isNull();
    }

    @Test
    void theDataProvidersAreGivenAndAbsentWhenEmpty() {
        assertThat(restaurant(withField("\"attributions\":[{\"provider\":\"P\",\"providerUri\":"
                + "\"https://example.test/p\"}]"), OPERA).attributions())
                .containsExactly(new DataProvider("P", "https://example.test/p"));
        assertThat(restaurant(withField("\"attributions\":[]"), OPERA).attributions()).isNull();
        assertThat(restaurant(withField(""), OPERA).attributions()).isNull();
    }

    // --- Diets (T035: T26, T27, T28, FR-009, D8) ---

    private List<String> diets(String fields) {
        return restaurant(withField(fields), OPERA).diets();
    }

    @Test
    void vegetarianIsStatedByServesVegetarianFoodOrTheVegetarianType() {
        assertThat(diets("\"servesVegetarianFood\":true")).containsExactly("vegetarian");
        assertThat(diets("\"types\":[\"vegetarian_restaurant\",\"restaurant\"]")).containsExactly("vegetarian");
    }

    @Test
    void veganAndHalalAreStatedByTheirTypesOnly() {
        assertThat(diets("\"types\":[\"vegan_restaurant\",\"restaurant\"]")).containsExactly("vegan");
        assertThat(diets("\"types\":[\"halal_restaurant\",\"vegetarian_restaurant\"],"
                + "\"servesVegetarianFood\":true")).containsExactly("halal", "vegetarian");
    }

    /** FR-009: false or absent never gives "not vegetarian"; nothing stated, no diet listed. */
    @Test
    void falseOrMissingIndicatorsListNoDiet() {
        assertThat(diets("\"servesVegetarianFood\":false")).isNull();
        assertThat(diets("\"types\":[\"restaurant\"]")).isNull();
    }
}
