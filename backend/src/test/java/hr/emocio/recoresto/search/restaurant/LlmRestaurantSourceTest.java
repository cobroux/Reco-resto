package hr.emocio.recoresto.search.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.llm.LlmChatClient;
import hr.emocio.recoresto.search.place.ApiAdresseClient;
import hr.emocio.recoresto.search.place.ResolvedPlace;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class LlmRestaurantSourceTest {

    private final LlmChatClient llm = mock(LlmChatClient.class);
    private final ApiAdresseClient geocoder = mock(ApiAdresseClient.class);
    private final LlmRestaurantSource source = new LlmRestaurantSource(llm, geocoder, JsonMapper.builder().build());

    private final ResolvedPlace center = new ResolvedPlace("Opéra, Paris", new Position(48.8708, 2.3322));
    private final SearchCriteria criteria = new SearchCriteria(List.of("asian"), List.of("vegetarian"), null,
            List.of("Opéra, Paris"), "now", null);
    private final ZonedDateTime moment = ZonedDateTime.of(2026, 11, 28, 12, 0, 0, 0, ZoneId.of("Europe/Paris"));

    private void llmReplies(String reply) {
        when(llm.complete(anyString(), anyString())).thenReturn(reply);
    }

    @Test
    void parsesRestaurantsAndComputesLocationAndDistanceFromTheGeocodedAddress() {
        llmReplies("""
                {"restaurants":[{"name":"Thai Garden","address":"10 rue Daunou, 75002 Paris","cuisine":"thai",
                  "diets":["vegetarian"],"keywords":["cosy"],"priceLevel":"€€","phone":"01 02 03 04 05",
                  "signatureDish":"pad thai","photoUrl":"https://img.test/1.jpg",
                  "openingHours":{"SATURDAY":[{"opens":"11:30","closes":"23:00"}]},
                  "menu":[{"name":"Pad thai","description":"noodles","price":"14"}]}]}""");
        when(geocoder.geocode("10 rue Daunou, 75002 Paris")).thenReturn(Optional.of(new Position(48.8690, 2.3320)));

        List<Restaurant> found = source.find(criteria, center, 2500, moment);

        assertThat(found).hasSize(1);
        Restaurant restaurant = found.get(0);
        assertThat(restaurant.name()).isEqualTo("Thai Garden");
        assertThat(restaurant.cuisine()).isEqualTo("thai");
        assertThat(restaurant.diets()).containsExactly("vegetarian");
        assertThat(restaurant.openingHours().get("SATURDAY")).containsExactly(new OpeningPeriod("11:30", "23:00"));
        assertThat(restaurant.menu()).hasSize(1);
        assertThat(restaurant.location()).isEqualTo(new Position(48.8690, 2.3320));
        assertThat(restaurant.distanceMeters()).isBetween(190, 215);
    }

    @Test
    void distanceGivenByTheModelIsNotTrusted() {
        llmReplies("""
                {"restaurants":[{"name":"X","address":"1 rue X","distanceMeters":5,
                  "location":{"latitude":1.0,"longitude":1.0}}]}""");
        when(geocoder.geocode("1 rue X")).thenReturn(Optional.of(new Position(48.8690, 2.3320)));

        Restaurant restaurant = source.find(criteria, center, 2500, moment).get(0);

        assertThat(restaurant.location()).isEqualTo(new Position(48.8690, 2.3320));
        assertThat(restaurant.distanceMeters()).isGreaterThan(100);
    }

    @Test
    void itemsWithoutANameAreDropped() {
        llmReplies("{\"restaurants\":[{\"address\":\"1 rue A\"},{\"name\":\"  \"},{\"name\":\"Ok\"}]}");

        assertThat(source.find(criteria, center, 2500, moment)).extracting(Restaurant::name).containsExactly("Ok");
    }

    @Test
    void missingFieldsAreLeftAbsentAndNeverInvented() {
        llmReplies("{\"restaurants\":[{\"name\":\"Bare\"}]}");

        Restaurant restaurant = source.find(criteria, center, 2500, moment).get(0);

        assertThat(restaurant.address()).isNull();
        assertThat(restaurant.phone()).isNull();
        assertThat(restaurant.openingHours()).isNull();
        assertThat(restaurant.location()).isNull();
        assertThat(restaurant.distanceMeters()).isNull();
        verify(geocoder, never()).geocode(anyString());
    }

    @Test
    void addressThatCannotBeGeocodedLeavesLocationAndDistanceAbsent() {
        llmReplies("{\"restaurants\":[{\"name\":\"Lost\",\"address\":\"somewhere\"}]}");
        when(geocoder.geocode("somewhere")).thenReturn(Optional.empty());

        Restaurant restaurant = source.find(criteria, center, 2500, moment).get(0);

        assertThat(restaurant.location()).isNull();
        assertThat(restaurant.distanceMeters()).isNull();
    }

    @Test
    void replyThatIsNotJsonGivesNoRestaurant() {
        llmReplies("I could not find anything");

        assertThat(source.find(criteria, center, 2500, moment)).isEmpty();
    }

    @Test
    void promptCarriesThePlaceRadiusMomentAndCriteria() {
        llmReplies("{\"restaurants\":[]}");

        source.find(criteria, center, 2500, moment);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), user.capture());
        String prompt = system.getValue() + user.getValue();
        assertThat(prompt).contains("Opéra, Paris").contains("2500").contains("2026-11-28T12:00")
                .contains("asian").contains("vegetarian");
    }
}
