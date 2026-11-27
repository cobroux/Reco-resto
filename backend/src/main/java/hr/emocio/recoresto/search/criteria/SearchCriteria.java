package hr.emocio.recoresto.search.criteria;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * What the model searched for, returned with the result. {@code query} is the short French text sent to Google Text
 * Search; {@code diets} are the diets the user requires, checked on the place; {@code moment} is "now" or an ISO-8601
 * date-time; {@code distanceLimitMeters} is the radius used.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchCriteria(String query, List<String> diets, String moment, Integer distanceLimitMeters) {

    public SearchCriteria {
        query = query == null || query.isBlank() ? null : query.trim();
        diets = diets == null ? List.of() : List.copyOf(diets);
    }
}
