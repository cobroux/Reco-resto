package hr.emocio.recoresto.search.restaurant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Third-party data provider Google requires to show with a place (spec 008, Google {@code attributions}). */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataProvider(String provider, String providerUri) {
}
