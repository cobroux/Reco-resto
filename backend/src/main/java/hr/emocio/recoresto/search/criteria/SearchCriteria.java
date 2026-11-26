package hr.emocio.recoresto.search.criteria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * What the user asked for. {@code query} is a short French text of what to eat, written by the model with the words of
 * the user and sent to Google Text Search (experiment of 2026-11-26); absent, the search is for any restaurant.
 * {@code diets} are the diets the user requires, checked on the place. {@code moment} is "now" or an ISO-8601
 * date-time; {@code distanceLimitMeters} replaces the default radius when present; {@code walkingMinutes} is a travel
 * time of the sentence (always on foot), turned into a distance with the walking speed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchCriteria(String query, List<String> diets, List<String> places, String moment,
        Integer distanceLimitMeters, Integer walkingMinutes) {

    public SearchCriteria {
        query = query == null || query.isBlank() ? null : query.trim();
        diets = diets == null ? List.of() : List.copyOf(diets);
        places = places == null ? List.of() : List.copyOf(places);
    }

    /**
     * Always empty since Google types are no longer chosen by the model; still in the answer because the response
     * contract of {@code POST /api/search} lists it. A value written by the model is ignored.
     */
    @JsonProperty(value = "cuisines", access = JsonProperty.Access.READ_ONLY)
    public List<String> cuisines() {
        return List.of();
    }
}
