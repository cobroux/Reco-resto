package hr.emocio.recoresto.search.restaurant;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.open;
import static org.assertj.core.api.Assertions.assertThat;

import hr.emocio.recoresto.search.config.ChainProperties;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DeckSelectorTest {

    private static final ChainProperties CHAINS = new ChainProperties(List.of("O'Tacos", "Paul", "Big Mamma"),
            List.of("facebook.com", "instagram.com", "tripadvisor.*"));

    private final DeckSelector selector = new DeckSelector(CHAINS);

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

    // Amendment (chains, product owner 2026-11-26): at most one card per chain in the deck.

    private static Restaurant site(String name, String website, Integer distanceMeters) {
        Restaurant base = open("x");
        return new Restaurant(name, base.location(), distanceMeters, base.cuisine(), base.diets(), base.keywords(),
                null, base.openingHours(), null, null, "1 rue " + name, null, null, website,
                "id-" + name + "-" + distanceMeters, null, null, null);
    }

    private static Restaurant named(String name, Integer distanceMeters) {
        return site(name, null, distanceMeters);
    }

    /** T48: the same website host (www and case ignored) is one chain; the nearest is kept. */
    @Test
    void theSameWebsiteHostIsOneChainAndTheNearestIsKept() {
        List<Restaurant> results = List.of(site("East Mamma", "https://www.bigmammagroup.com/east", 800),
                site("Libertino", "http://BigMammaGroup.com/libertino", 300), named("Other", 500));

        DeckSelector.Selection selection = selector.select(List.of(results), 15);

        assertThat(names(selection.deck())).containsExactly("Libertino", "Other");
        assertThat(selection.zone()).hasSize(3);
    }

    /** T49: a generic host shared by unrelated businesses never groups restaurants. */
    @Test
    void aGenericHostDoesNotGroupRestaurants() {
        List<Restaurant> results = List.of(site("Chez Lulu", "https://www.facebook.com/chezlulu", 100),
                site("Le Petit Zinc", "https://fr-fr.facebook.com/lepetitzinc", 200),
                site("Bistrot", "https://www.tripadvisor.fr/Restaurant_Review-1", 300),
                site("Brasserie", "https://www.tripadvisor.com/Restaurant_Review-2", 400),
                site("Insta", "instagram.com/insta", 500), site("Gram", "https://instagram.com/gram", 600));

        assertThat(selector.select(List.of(results), 15).deck()).hasSize(6);
    }

    /** Per-site subdomains of a website builder (wixsite.com) keep their full host and are distinct chains. */
    @Test
    void differentSubdomainsAreDifferentHosts() {
        List<Restaurant> results = List.of(site("One", "https://one.wixsite.com/site", 100),
                site("Two", "https://two.wixsite.com/site", 200));

        assertThat(selector.select(List.of(results), 15).deck()).hasSize(2);
    }

    /** T50: the same normalized name, the part after " - ", " | " or "(" dropped, is one chain. */
    @Test
    void theSameNormalizedNameWithASuffixIsOneChain() {
        List<Restaurant> results = List.of(named("Big Mama - East Mamma", 900), named("BIG MAMA | Pink", 400),
                named("Crêperie Bretonne (Montparnasse)", 300), named("creperie  bretonne!", 200));

        assertThat(names(selector.select(List.of(results), 15).deck()))
                .containsExactly("BIG MAMA | Pink", "creperie  bretonne!");
    }

    /** T51: a name starting with a known chain name belongs to that chain, even with different names. */
    @Test
    void aKnownChainPrefixGroupsDifferentNames() {
        List<Restaurant> results = List.of(named("O'Tacos Châtelet", 700), named("O Tacos République", 250),
                named("Paul", 600), named("Paul Gare de Lyon", 650), named("Paulette", 50));

        assertThat(names(selector.select(List.of(results), 15).deck()))
                .containsExactly("O Tacos République", "Paul", "Paulette");
    }

    @Test
    void onEqualDistancesTheFirstInSourceOrderIsKept() {
        List<Restaurant> results = List.of(named("Big Mamma A", 300), named("Big Mamma B", 300));

        assertThat(names(selector.select(List.of(results), 15).deck())).containsExactly("Big Mamma A");
    }

    @Test
    void aKnownDistanceIsNearerThanAnUnknownOne() {
        List<Restaurant> results = List.of(named("Big Mamma A", null), named("Big Mamma B", 900));

        assertThat(names(selector.select(List.of(results), 15).deck())).containsExactly("Big Mamma B");
    }

    /** T52: the freed slots go to the next restaurants of the zone; the zone is unchanged. */
    @Test
    void freedSlotsAreRefilledFromTheZoneAndTheZoneIsUnchanged() {
        List<Restaurant> results = List.of(named("Paul Opéra", 100), named("Paul Louvre", 200), named("A", 300),
                named("Paul Bastille", 400), named("B", 500), named("C", 600));

        DeckSelector.Selection selection = selector.select(List.of(results), 3);

        assertThat(names(selection.deck())).containsExactly("Paul Opéra", "A", "B");
        assertThat(selection.zone()).isEqualTo(results);
    }

    /** Chains are found across the merged results of several places. */
    @Test
    void chainsAreFoundAcrossSeveralPlaces() {
        List<Restaurant> first = List.of(named("O'Tacos Châtelet", 700), named("A", 100));
        List<Restaurant> second = List.of(named("O Tacos République", 250));

        assertThat(names(selector.select(List.of(first, second), 15).deck()))
                .containsExactly("A", "O Tacos République");
    }

    /** T53: no website and no usable name never group restaurants. */
    @Test
    void noWebsiteAndNoUsableNameDoNotGroup() {
        List<Restaurant> results = List.of(site(null, null, 100), site(null, null, 200), site("", "", 300),
                site("-", "not a url", 400), site("( )", "https://", 500));

        assertThat(selector.select(List.of(results), 15).deck()).hasSize(5);
    }

    @Test
    void aWebsiteWithoutSchemeIsReadAsAHost() {
        List<Restaurant> results = List.of(site("One", "www.bigmammagroup.com", 300),
                site("Two", "https://bigmammagroup.com/", 100));

        assertThat(names(selector.select(List.of(results), 15).deck())).containsExactly("Two");
    }

    @Test
    void withoutConfiguredChainsOnlyHostsAndNamesGroup() {
        DeckSelector plain = new DeckSelector(new ChainProperties(null, null));
        List<Restaurant> results = List.of(named("O'Tacos Châtelet", 700), named("O Tacos République", 250),
                site("Chez Lulu", "https://facebook.com/chezlulu", 100),
                site("Le Petit Zinc", "https://facebook.com/lepetitzinc", 200));

        assertThat(names(plain.select(List.of(results), 15).deck()))
                .containsExactly("O'Tacos Châtelet", "O Tacos République", "Chez Lulu");
    }
}
