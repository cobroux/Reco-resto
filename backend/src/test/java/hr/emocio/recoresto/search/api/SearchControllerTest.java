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
import hr.emocio.recoresto.search.restaurant.Attribution;
import hr.emocio.recoresto.search.restaurant.DataProvider;
import hr.emocio.recoresto.search.restaurant.Restaurant;
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

    /** 008 FR-011, T32: the new fields are optional additions; every existing field keeps its name. */
    @Test
    void theOptionalGoogleFieldsAreSerializedUnderTheirContractNames() throws Exception {
        Restaurant base = open("G");
        Restaurant google = new Restaurant(base.name(), base.location(), base.distanceMeters(), "Restaurant thaï",
                List.of("vegetarian"), null, "€€", base.openingHours(), "/api/photos/p1/ph1", null, base.address(),
                "01 23 45 67 89", null, "https://example.test/g", "p1",
                List.of(new Attribution("Author", "https://example.test/author")), "https://example.test/photo",
                List.of(new DataProvider("Provider", "https://example.test/provider")));
        when(service.search(any(), eq(false))).thenReturn(new SearchResult(criteria, List.of(google), null));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"thaï\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deck[0].name").value("G"))
                .andExpect(jsonPath("$.deck[0].location.latitude").value(base.location().latitude()))
                .andExpect(jsonPath("$.deck[0].distanceMeters").value(500))
                .andExpect(jsonPath("$.deck[0].cuisine").value("Restaurant thaï"))
                .andExpect(jsonPath("$.deck[0].diets[0]").value("vegetarian"))
                .andExpect(jsonPath("$.deck[0].priceLevel").value("€€"))
                .andExpect(jsonPath("$.deck[0].openingHours.MONDAY[0].opens").value("00:00"))
                .andExpect(jsonPath("$.deck[0].photoUrl").value("/api/photos/p1/ph1"))
                .andExpect(jsonPath("$.deck[0].address").value("1 rue G"))
                .andExpect(jsonPath("$.deck[0].phone").value("01 23 45 67 89"))
                .andExpect(jsonPath("$.deck[0].website").value("https://example.test/g"))
                .andExpect(jsonPath("$.deck[0].placeId").value("p1"))
                .andExpect(jsonPath("$.deck[0].photoAttributions[0].displayName").value("Author"))
                .andExpect(jsonPath("$.deck[0].photoAttributions[0].uri").value("https://example.test/author"))
                .andExpect(jsonPath("$.deck[0].photoSourceUri").value("https://example.test/photo"))
                .andExpect(jsonPath("$.deck[0].attributions[0].provider").value("Provider"))
                .andExpect(jsonPath("$.deck[0].attributions[0].providerUri").value("https://example.test/provider"));
    }

    @Test
    void absentGoogleFieldsAreOmitted() throws Exception {
        when(service.search(any(), eq(false))).thenReturn(new SearchResult(criteria, List.of(open("A")), null));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"thaï\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deck[0].website").doesNotExist())
                .andExpect(jsonPath("$.deck[0].placeId").doesNotExist())
                .andExpect(jsonPath("$.deck[0].photoAttributions").doesNotExist())
                .andExpect(jsonPath("$.deck[0].photoSourceUri").doesNotExist())
                .andExpect(jsonPath("$.deck[0].attributions").doesNotExist());
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

    /** 008 T33-T37: any Google failure is 503 SERVICE_UNAVAILABLE ("Réessayer"). */
    @Test
    void aSourceFailureIsServiceUnavailable() throws Exception {
        when(service.search(any(), eq(false)))
                .thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE, "Google Places is unavailable"));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"thaï à Opéra\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    /** 008 T38: Google found nothing, the 002 "no result" outcome. */
    @Test
    void anEmptyGoogleAnswerIsNoResult() throws Exception {
        when(service.search(any(), eq(false)))
                .thenThrow(new SearchException(SearchErrorCode.NO_RESULT, "No restaurant matches the search"));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sentence\":\"thaï à Opéra\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_RESULT"));
    }
}
