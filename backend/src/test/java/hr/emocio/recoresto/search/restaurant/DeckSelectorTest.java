package hr.emocio.recoresto.search.restaurant;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.open;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DeckSelectorTest {

    private final DeckSelector selector = new DeckSelector();

    private static List<Restaurant> restaurants(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> open("r" + i)).toList();
    }

    private static List<String> names(List<Restaurant> restaurants) {
        return restaurants.stream().map(Restaurant::name).toList();
    }

    @Test
    void moreMatchesThanTheDeckSizeGivesExactlyTheDeckSizeInOrder() {
        DeckSelector.Selection selection = selector.select(List.of(restaurants(20)), 15);

        assertThat(selection.deck()).hasSize(15);
        assertThat(names(selection.deck())).isEqualTo(names(restaurants(15)));
        assertThat(selection.zone()).hasSize(20);
    }

    @Test
    void fewerMatchesThanTheDeckSizeGivesAllOfThem() {
        DeckSelector.Selection selection = selector.select(List.of(restaurants(5)), 15);

        assertThat(selection.deck()).hasSize(5);
        assertThat(selection.zone()).hasSize(5);
    }

    @Test
    void resultsOfSeveralPlacesAreMergedWithoutDuplicates() {
        List<Restaurant> first = List.of(open("A"), open("B"));
        List<Restaurant> second = List.of(open("b"), open("C"));

        DeckSelector.Selection selection = selector.select(List.of(first, second), 15);

        assertThat(names(selection.zone())).containsExactly("A", "B", "C");
        assertThat(names(selection.deck())).containsExactly("A", "B", "C");
    }

    private static Restaurant google(String name, String address, String placeId, int distanceMeters) {
        Restaurant base = open(name);
        return new Restaurant(name, base.location(), distanceMeters, base.cuisine(), base.diets(), base.keywords(),
                null, base.openingHours(), null, null, address, null, null, null, placeId, null, null, null);
    }

    /** 008 T7: the same Google place found around two places appears once, at its nearest distance. */
    @Test
    void theSamePlaceIdFoundAroundTwoPlacesAppearsOnceAtItsNearestDistance() {
        List<Restaurant> first = List.of(google("A", "1 rue A", "id-a", 900), google("B", "1 rue B", "id-b", 100));
        List<Restaurant> second = List.of(google("A (other name)", "other address", "id-a", 300),
                google("C", "1 rue C", "id-c", 200));

        DeckSelector.Selection selection = selector.select(List.of(first, second), 15);

        assertThat(names(selection.zone())).containsExactly("A", "B", "C");
        assertThat(selection.zone().get(0).distanceMeters()).isEqualTo(300);
    }

    @Test
    void twoDifferentPlaceIdsAreKeptEvenWithTheSameNameAndAddress() {
        List<Restaurant> results = List.of(google("A", "1 rue A", "id-1", 100), google("A", "1 rue A", "id-2", 200));

        assertThat(selector.select(List.of(results), 15).zone()).hasSize(2);
    }

    @Test
    void withoutPlaceIdTheNameAndAddressStayTheKey() {
        List<Restaurant> results = List.of(google("A", "1 rue A", null, 100), google("a", "1 RUE A", null, 200));

        assertThat(selector.select(List.of(results), 15).zone()).hasSize(1);
    }

    @Test
    void noRestaurantGivesAnEmptySelection() {
        DeckSelector.Selection selection = selector.select(List.of(), 15);

        assertThat(selection.deck()).isEmpty();
        assertThat(selection.zone()).isEmpty();
    }
}
