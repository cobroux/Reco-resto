package hr.emocio.recoresto.search.criteria;

import hr.emocio.recoresto.search.google.GooglePlace;
import hr.emocio.recoresto.search.google.GooglePlaceTypes;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Checks the diets of the criteria on a Google place (spec 008 D8, FR-009). What to eat is no longer matched here:
 * Google Text Search finds it from the query (experiment of 2026-11-26).
 * <ul>
 * <li>Every diet the user requires must be stated by the place; no diet matches every place.</li>
 * <li>A diet Google never states (for example kosher) matches no place.</li>
 * </ul>
 */
@Component
public class CriteriaMatcher {

    /** Whether the place states every diet of the criteria. */
    public boolean matches(SearchCriteria criteria, GooglePlace place) {
        List<String> stated = GooglePlaceTypes.statedDiets(place.types(), place.servesVegetarianFood());
        return criteria.diets().stream().allMatch(diet -> diet != null
                && stated.contains(diet.trim().toLowerCase(Locale.ROOT)));
    }
}
