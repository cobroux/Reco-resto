package hr.emocio.recoresto.search.api;

import hr.emocio.recoresto.search.config.CardEnrichmentProperties;
import hr.emocio.recoresto.search.enrichment.CardEnrichment;
import hr.emocio.recoresto.search.enrichment.CardEnrichmentService;
import hr.emocio.recoresto.search.enrichment.CuisinePhotos;
import hr.emocio.recoresto.search.enrichment.WebsiteAddress;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Photo of one restaurant card taken from its website, or an Unsplash photo of its cuisine as the last resort (spec
 * 007, amendment (b), contracts/card-enrichment.openapi.yaml). The website
 * must pass the single website rule (research R3), else 400 without body, as {@link PhotoController} does; a
 * {@code cuisine} longer than {@link CuisinePhotos#MAX_CUISINE_LENGTH} characters is also a 400 without body (it is
 * the Unsplash query, PR #15 review). Any
 * enrichment failure is a 200 without photo: the user never sees an error (FR-012).
 */
@RestController
@RequestMapping("/api/card-enrichment")
public class CardEnrichmentController {

    private static final Logger log = LoggerFactory.getLogger(CardEnrichmentController.class);

    private final CardEnrichmentService service;
    private final CardEnrichmentProperties properties;

    public CardEnrichmentController(CardEnrichmentService service, CardEnrichmentProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /**
     * @param website the {@code Restaurant.website} of the search response
     * @param cuisine the {@code Restaurant.cuisine} shown on the card, the Unsplash query of amendment (b); optional
     */
    @GetMapping
    public ResponseEntity<CardEnrichment> enrich(@RequestParam(required = false) String website,
            @RequestParam(required = false) String cuisine) {
        Optional<WebsiteAddress> address = WebsiteAddress.parse(website, properties.allowedPorts());
        if (address.isEmpty() || (cuisine != null && cuisine.length() > CuisinePhotos.MAX_CUISINE_LENGTH)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(service.enrich(address.get(), cuisine));
        } catch (RuntimeException e) {
            log.warn("Card enrichment failed unexpectedly for {}: {}", address.get().host(),
                    e.getClass().getSimpleName());
            return ResponseEntity.ok(CardEnrichment.none());
        }
    }
}
