package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.criteria.CriteriaMatcher;
import hr.emocio.recoresto.search.criteria.SearchCriteria;
import hr.emocio.recoresto.search.google.GooglePlace;
import hr.emocio.recoresto.search.google.GooglePlaceTypes;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Keeps only restaurants that can be offered: with an address (a route must be possible), within the radius, and not
 * known to be closed at the targeted moment. A restaurant whose distance is unknown is excluded because the radius
 * cannot be confirmed. One whose opening hours are unknown is kept (decision of 2026-11-25, a deviation from spec
 * 002 FR-007 to report in the PR: the model often leaves the hours out or gives a single day; confirmed for Google by
 * spec 008 D9 (1)). Spec 008 adds, on the Google place: a restaurant type (D6), a business status that is not closed
 * or future (D9 (3)), and the AND/OR criteria ({@link CriteriaMatcher}).
 */
@Component
public class RestaurantFilters {

    /** D9 (3): Google business statuses excluded; any other status, or none, is kept. */
    private static final Set<String> EXCLUDED_STATUSES = Set.of("CLOSED_TEMPORARILY", "CLOSED_PERMANENTLY",
            "FUTURE_OPENING");

    private final CriteriaMatcher matcher;

    public RestaurantFilters(CriteriaMatcher matcher) {
        this.matcher = matcher;
    }

    public List<Restaurant> apply(List<GooglePlace> places, SearchCriteria criteria, double radiusMeters,
            ZonedDateTime moment) {
        return places.stream()
                .filter(place -> GooglePlaceTypes.isRestaurant(place.primaryType()))
                .filter(place -> place.businessStatus() == null || !EXCLUDED_STATUSES.contains(place.businessStatus()))
                .filter(place -> matcher.matches(criteria, place))
                .map(GooglePlace::restaurant)
                .filter(this::hasAddress)
                .filter(restaurant -> isWithin(restaurant, radiusMeters))
                .filter(restaurant -> isOpen(restaurant, moment))
                .toList();
    }

    private boolean hasAddress(Restaurant restaurant) {
        return restaurant.address() != null && !restaurant.address().isBlank();
    }

    private boolean isWithin(Restaurant restaurant, double radiusMeters) {
        return restaurant.location() != null && restaurant.distanceMeters() != null
                && restaurant.distanceMeters() <= radiusMeters;
    }

    private boolean isOpen(Restaurant restaurant, ZonedDateTime moment) {
        Map<String, List<OpeningPeriod>> hours = restaurant.openingHours();
        if (hours == null || hours.values().stream().allMatch(periods -> periods == null || periods.isEmpty())) {
            return true; // unknown opening hours: not known to be closed
        }
        List<OpeningPeriod> periods = hours.get(moment.getDayOfWeek().name());
        if (periods == null) {
            return false;
        }
        LocalTime time = moment.toLocalTime();
        return periods.stream().anyMatch(period -> contains(period, time));
    }

    private boolean contains(OpeningPeriod period, LocalTime time) {
        try {
            LocalTime opens = LocalTime.parse(period.opens());
            LocalTime closes = LocalTime.parse(period.closes());
            return !time.isBefore(opens) && (!closes.isAfter(opens) || time.isBefore(closes));
        } catch (DateTimeParseException | NullPointerException e) {
            return false;
        }
    }
}
