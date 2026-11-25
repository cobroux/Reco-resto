package hr.emocio.recoresto.search.criteria;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * What the user asked for. {@code moment} is "now" or an ISO-8601 date-time; {@code distanceLimitMeters} replaces the
 * default radius when present; {@code walkingMinutes} is a travel time of the sentence (always on foot), turned into a
 * distance with the walking speed. Budget and specific dish are deliberately not part of the criteria.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchCriteria(List<String> cuisines, List<String> diets, CriteriaNode combination,
        List<String> places, String moment, Integer distanceLimitMeters, Integer walkingMinutes) {

    public SearchCriteria {
        cuisines = cuisines == null ? List.of() : List.copyOf(cuisines);
        diets = diets == null ? List.of() : List.copyOf(diets);
        places = places == null ? List.of() : List.copyOf(places);
    }
}
