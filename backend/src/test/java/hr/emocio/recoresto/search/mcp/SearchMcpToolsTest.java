package hr.emocio.recoresto.search.mcp;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.emocio.recoresto.search.SearchService;
import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.api.SearchErrorCode;
import hr.emocio.recoresto.search.api.SearchException;
import hr.emocio.recoresto.search.api.SearchRequest;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchMcpToolsTest {

    private final SearchService service = mock(SearchService.class);

    private final SearchMcpTools tools = new SearchMcpTools(service);
    private final SearchResult result =
            new SearchResult(new SearchCriteria("italien", List.of(), "now", null), List.of(open("Trattoria")), null);

    @Test
    void searchesWithTheSentenceAndNoPosition() {
        SearchRequest request = new SearchRequest("italien près d'Opéra", null);
        when(service.search(request, false)).thenReturn(result);

        assertThat(tools.searchRestaurants("italien près d'Opéra", null, null)).isSameAs(result);
        verify(service).search(request, false);
    }

    @Test
    void passesThePositionWhenBothCoordinatesAreGiven() {
        SearchRequest request = new SearchRequest("italien", new Position(48.87, 2.33));
        when(service.search(request, false)).thenReturn(result);

        assertThat(tools.searchRestaurants("italien", 48.87, 2.33)).isSameAs(result);
    }

    @Test
    void aSingleCoordinateIsRefused() {
        assertThatThrownBy(() -> tools.searchRestaurants("italien", 48.87, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchErrorsPropagateSoThatTheClientSeesAToolError() {
        when(service.search(new SearchRequest("blabla", null), false))
                .thenThrow(new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "cannot be understood"));

        assertThatThrownBy(() -> tools.searchRestaurants("blabla", null, null)).isInstanceOf(SearchException.class);
    }
}
