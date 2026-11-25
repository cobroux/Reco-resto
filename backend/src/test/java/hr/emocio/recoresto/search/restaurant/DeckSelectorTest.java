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

    @Test
    void noRestaurantGivesAnEmptySelection() {
        DeckSelector.Selection selection = selector.select(List.of(), 15);

        assertThat(selection.deck()).isEmpty();
        assertThat(selection.zone()).isEmpty();
    }
}
