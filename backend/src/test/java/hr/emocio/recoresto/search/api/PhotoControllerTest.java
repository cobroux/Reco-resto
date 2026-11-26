package hr.emocio.recoresto.search.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hr.emocio.recoresto.search.google.GooglePhoto;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Spec 008 T29, T30, T39; contracts/search-api.openapi.yaml {@code /api/photos/{placeId}/{photoId}} (D16 a). */
@WebMvcTest(PhotoController.class)
class PhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GooglePlacesClient client;

    @Test
    void returnsTheImageWithItsContentTypeAndNoKey() throws Exception {
        when(client.photo("p1", "ph_1-x")).thenReturn(Optional.of(new GooglePhoto(new byte[] {1, 2, 3},
                "image/jpeg")));

        mockMvc.perform(get("/api/photos/p1/ph_1-x"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().doesNotExist("X-Goog-Api-Key"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    /** PR #15 review: an unreadable or non-image type from Google is never served (the card keeps the tablecloth). */
    @ParameterizedTest
    @ValueSource(strings = {"not a type", "", "text/html", "application/octet-stream"})
    void anUnreadableOrNonImageTypeIsABadGatewayWithoutBody(String type) throws Exception {
        when(client.photo(any(), any())).thenReturn(Optional.of(new GooglePhoto(new byte[] {1, 2, 3}, type)));

        mockMvc.perform(get("/api/photos/p1/ph1"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(""));
    }

    /** Research R5: identifiers only, so the endpoint can never be used as an open proxy. */
    @Test
    void parametersOutsideIdentifierCharactersAreABadRequestWithoutCallingGoogle() throws Exception {
        mockMvc.perform(get("/api/photos/p1/a.b")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/photos/p$1/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/photos/p1/a%3Ab")).andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }

    @Test
    void anUnknownOrExpiredPhotoIsNotFound() throws Exception {
        when(client.photo(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/photos/p1/old")).andExpect(status().isNotFound());
    }

    /** T30, T33-T36: a Google failure or a missing key, the card then shows the tablecloth. */
    @Test
    void aGoogleFailureIsServiceUnavailable() throws Exception {
        when(client.photo(any(), any())).thenThrow(new SearchException(SearchErrorCode.SERVICE_UNAVAILABLE,
                "Google Places is unavailable"));

        mockMvc.perform(get("/api/photos/p1/ph1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
}
