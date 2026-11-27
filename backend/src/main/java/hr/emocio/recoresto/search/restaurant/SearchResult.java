package hr.emocio.recoresto.search.restaurant;

import com.fasterxml.jackson.annotation.JsonInclude;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import java.util.List;

/** The understood criteria, the deck, and every restaurant of the zone when it was requested. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(SearchCriteria criteria, List<Restaurant> deck, List<Restaurant> zone) {
}
