package hr.emocio.recoresto.search.api;

import static org.mockito.ArgumentMatchers.any;
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
 * Spec 007 T3, FR-012; contracts/card-enrichment.openapi.yaml {@code GET /api/card-enrichment}; data-model.md "Card
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
        when(service.enrich(any())).thenReturn(CardEnrichment.photo("https://cdn.example.test/salle.jpg",
                EnrichmentOutcome.SHARING_IMAGE));

        mockMvc.perform(get("/api/card-enrichment").param("website", "https://www.example-restaurant.test/"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"photoUrl\":\"https://cdn.example.test/salle.jpg\","
                        + "\"photoOrigin\":\"SHARING_IMAGE\"}", JsonCompareMode.STRICT));
        verify(service).enrich(address("https://www.example-restaurant.test/"));
    }

    @Test
    void noPhotoIsAnEmptyObjectWithoutNullFields() throws Exception {
        when(service.enrich(any())).thenReturn(CardEnrichment.none());

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
        when(service.enrich(any())).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/card-enrichment").param("website", "https://x.test/"))
                .andExpect(status().isOk())
                .andExpect(content().string("{}"));
    }

}
