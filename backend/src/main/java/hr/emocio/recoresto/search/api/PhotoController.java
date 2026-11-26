package hr.emocio.recoresto.search.api;

import hr.emocio.recoresto.search.google.GooglePlaceMapper;
import hr.emocio.recoresto.search.google.GooglePlacesClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the photos of the cards through the backend, so that the Google key never reaches the browser (spec 008
 * D16 a, FR-010, FR-013). Only identifiers are accepted, so the endpoint can only call the Google photo media of a
 * place (research R5). An error status lets the card fall back to the tablecloth.
 */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final GooglePlacesClient client;

    public PhotoController(GooglePlacesClient client) {
        this.client = client;
    }

    @GetMapping("/{placeId}/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable String placeId, @PathVariable String photoId) {
        if (!placeId.matches(GooglePlaceMapper.IDENTIFIER) || !photoId.matches(GooglePlaceMapper.IDENTIFIER)) {
            return ResponseEntity.badRequest().build();
        }
        return client.photo(placeId, photoId)
                .map(photo -> ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                        .body(photo.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
