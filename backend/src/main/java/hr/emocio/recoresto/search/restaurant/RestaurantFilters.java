package hr.emocio.recoresto.search.restaurant;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Keeps only restaurants that can be offered: with an address (a route must be possible), within the radius, and
 * open at the targeted moment. A restaurant whose distance or opening hours are unknown is excluded because the
 * requirement cannot be confirmed.
 */
@Component
public class RestaurantFilters {

    public List<Restaurant> apply(List<Restaurant> restaurants, double radiusMeters, ZonedDateTime moment) {
        return restaurants.stream()
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
        if (hours == null) {
            return false;
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
