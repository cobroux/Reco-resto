package hr.emocio.recoresto.search.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.api.Position;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceResolverTest {

    private final ApiAdresseClient client = mock(ApiAdresseClient.class);
    private final PlaceResolver resolver = new PlaceResolver(client);

    @Test
    void resolvesEveryPlaceNamedInTheSentence() {
        when(client.geocode("Opéra")).thenReturn(Optional.of(new Position(48.87, 2.33)));
        when(client.geocode("Châtelet")).thenReturn(Optional.of(new Position(48.86, 2.35)));

        assertThat(resolver.resolve(List.of("Opéra", "Châtelet"))).containsExactly(
                new ResolvedPlace("Opéra", new Position(48.87, 2.33)),
                new ResolvedPlace("Châtelet", new Position(48.86, 2.35)));
    }

    @Test
    void placesThatCannotBeResolvedAreOmitted() {
        when(client.geocode("Opéra")).thenReturn(Optional.of(new Position(48.87, 2.33)));
        when(client.geocode("blabla")).thenReturn(Optional.empty());

        assertThat(resolver.resolve(List.of("blabla", "Opéra"))).extracting(ResolvedPlace::name)
                .containsExactly("Opéra");
    }

    @Test
    void noPlaceGivesNoResolvedPlace() {
        assertThat(resolver.resolve(List.of())).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
    }
}
