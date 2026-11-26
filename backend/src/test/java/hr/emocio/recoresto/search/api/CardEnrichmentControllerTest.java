package hr.emocio.recoresto.search.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.enrichment.CardEnrichment;
import hr.emocio.recoresto.search.enrichment.CardEnrichmentService;
import hr.emocio.recoresto.search.enrichment.EnrichmentOutcome;
import hr.emocio.recoresto.search.enrichment.PhotoCredit;
import hr.emocio.recoresto.search.enrichment.WebsiteAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec 007 T3, FR-012; amendment (b) T39, FR-015 ({@code cuisine}, {@code credit}); contracts/card-enrichment.openapi.yaml {@code GET /api/card-enrichment}; data-model.md "Card
 * enrichment request" ({@code website}).
 */
@WebMvcTest(CardEnrichmentController.class)
@EnableConfigurationProperties(CardEnrichmentProperties.class)
class CardEnrichmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardEnrichmentService service;

    private static WebsiteAddress address(String value) {
        return new WebsiteAddress(URI.create(value));
    }

    @Test
    void aPhotoIsAnsweredWithItsOriginOnly() throws Exception {
        when(service.enrich(any(), any())).thenReturn(CardEnrichment.photo("https://cdn.example.test/salle.jpg",
                EnrichmentOutcome.SHARING_IMAGE));

        mockMvc.perform(get("/api/card-enrichment").param("website", "https://www.example-restaurant.test/"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"photoUrl\":\"https://cdn.example.test/salle.jpg\","
                        + "\"photoOrigin\":\"SHARING_IMAGE\"}", JsonCompareMode.STRICT));
        verify(service).enrich(address("https://www.example-restaurant.test/"), null);
    }

    @Test
    void noPhotoIsAnEmptyObjectWithoutNullFields() throws Exception {
        when(service.enrich(any(), any())).thenReturn(CardEnrichment.none());

        mockMvc.perform(get("/api/card-enrichment").param("website", "https://www.example-restaurant.test/"))
                .andExpect(status().isOk())
                .andExpect(content().string("{}"));
    }

    /** T3: missing or invalid website, 400 without body and no enrichment. */
    @ParameterizedTest
    @ValueSource(strings = {"", "ftp://example.com", "monresto", "/menu", "https://user:pw@x.test/",
            "http://x.test:8080/", "http://exa mple.test/"})
    void anInvalidWebsiteIsABadRequestWithoutBody(String website) throws Exception {
        mockMvc.perform(get("/api/card-enrichment").param("website", website))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verifyNoInteractions(service);
    }

    @Test
    void aMissingWebsiteIsABadRequestWithoutBody() throws Exception {
        mockMvc.perform(get("/api/card-enrichment"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        verifyNoInteractions(service);
    }

    /** FR-012: an enrichment failure is never an error for the user. */
    @Test
    void anUnexpectedFailureIsStillNoPhoto() throws Exception {
        when(service.enrich(any(), any())).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/card-enrichment").param("website", "https://x.test/"))
                .andExpect(status().isOk())
                .andExpect(content().string("{}"));
    }

    /** Amendment (b): the cuisine shown on the card is forwarded as is. */
    @Test
    void theCuisineIsForwardedToTheService() throws Exception {
        when(service.enrich(any(), any())).thenReturn(CardEnrichment.none());

        mockMvc.perform(get("/api/card-enrichment").param("website", "https://x.test/")
                .param("cuisine", "Restaurant italien"))
                .andExpect(status().isOk());
        verify(service).enrich(eq(address("https://x.test/")), eq("Restaurant italien"));
    }

    /** FR-015, contract {@code PhotoCredit}; T39: nothing else, no key. */
    @Test
    void anUnsplashPhotoIsAnsweredWithItsCredit() throws Exception {
        when(service.enrich(any(), any())).thenReturn(CardEnrichment.cuisinePhoto(
                "https://images.unsplash.test/photo-1?w=1080", new PhotoCredit("Jane Doe",
                        "https://unsplash.com/@jane?utm_source=reco_resto&utm_medium=referral",
                        "https://unsplash.com/?utm_source=reco_resto&utm_medium=referral")));

        mockMvc.perform(get("/api/card-enrichment").param("website", "https://x.test/").param("cuisine", "Pizzeria"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"photoUrl\":\"https://images.unsplash.test/photo-1?w=1080\","
                        + "\"photoOrigin\":\"UNSPLASH_CUISINE_PHOTO\",\"credit\":{\"photographerName\":\"Jane Doe\","
                        + "\"photographerUrl\":\"https://unsplash.com/@jane?utm_source=reco_resto&utm_medium=referral\","
                        + "\"unsplashUrl\":\"https://unsplash.com/?utm_source=reco_resto&utm_medium=referral\"}}",
                        JsonCompareMode.STRICT));
    }
}
