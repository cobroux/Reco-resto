package hr.emocio.recoresto.search.api;

import static hr.emocio.recoresto.search.restaurant.TestRestaurants.open;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hr.emocio.recoresto.search.SearchService;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.restaurant.SearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService service;

    private final SearchCriteria criteria = new SearchCriteria(List.of("asian"), List.of("vegetarian"), null,
            List.of("Opéra, Paris"), "now", null, null);

    @Test
    void returnsTheCriteriaAndTheDeckWithoutTheZoneByDefault() throws Exception {
        when(service.search(any(), eq(false)))
                .thenReturn(new SearchResult(criteria, List.of(open("Thai")), null));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"asiatique près d'Opéra\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria.cuisines[0]").value("asian"))
                .andExpect(jsonPath("$.criteria.moment").value("now"))
                .andExpect(jsonPath("$.deck[0].name").value("Thai"))
                .andExpect(jsonPath("$.deck[0].address").value("1 rue Thai"))
                .andExpect(jsonPath("$.zone").doesNotExist());
        verify(service).search(new SearchRequest("asiatique près d'Opéra", null), false);
    }

    @Test
    void includeZoneReturnsEveryRestaurantOfTheZone() throws Exception {
        when(service.search(any(), eq(true))).thenReturn(
                new SearchResult(criteria, List.of(open("A")), List.of(open("A"), open("B"))));

        mockMvc.perform(post("/api/search").param("includeZone", "true").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"asiatique\",\"position\":{\"latitude\":48.87,\"longitude\":2.33}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone.length()").value(2));
        verify(service).search(new SearchRequest("asiatique", new Position(48.87, 2.33)), true);
    }

    @Test
    void blankSentenceIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("{\"sentence\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchErrorsUseTheirCodeAndStatus() throws Exception {
        when(service.search(any(), eq(false)))
                .thenThrow(new SearchException(SearchErrorCode.NOT_UNDERSTOOD, "cannot be understood"));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"blablabla\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_UNDERSTOOD"));
    }
}
