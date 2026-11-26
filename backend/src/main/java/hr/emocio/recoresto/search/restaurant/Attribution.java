package hr.emocio.recoresto.search.restaurant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Author of a photo, to show with it (spec 008 FR-014, Google {@code authorAttributions}). */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Attribution(String displayName, String uri) {
}
