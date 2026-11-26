package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.google.GooglePlace;
import java.util.List;

/** Where restaurants come from (spec 008: Google Places), replaceable without touching the rest. */
public interface RestaurantSource {

    /**
     * Finds the places matching the criteria around each search point, within the radius. The search is built by the
     * backend from the criteria, never from text written by the model (FR-002). One list per search point, in the
     * order of the points.
     */
    List<List<GooglePlace>> find(SearchCriteria criteria, List<Position> searchPoints, double radiusMeters);
}
