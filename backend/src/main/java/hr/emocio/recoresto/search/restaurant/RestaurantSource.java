package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.place.ResolvedPlace;
import java.time.ZonedDateTime;
import java.util.List;

/** Where restaurants come from; today the language model, replaceable without touching the rest. */
public interface RestaurantSource {

    List<Restaurant> find(SearchCriteria criteria, ResolvedPlace center, double radiusMeters, ZonedDateTime moment);
}
