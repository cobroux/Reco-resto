package hr.emocio.recoresto.search.restaurant;

import hr.emocio.recoresto.search.api.Position;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared builders for restaurant tests. */
public final class TestRestaurants {

    public static final Position CENTER = new Position(48.8708, 2.3322);

    private TestRestaurants() {
    }

    public static Map<String, List<OpeningPeriod>> everyDay(String opens, String closes) {
        Map<String, List<OpeningPeriod>> hours = new LinkedHashMap<>();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            hours.put(day, List.of(new OpeningPeriod(opens, closes)));
        }
        return hours;
    }

    public static Restaurant of(String name, String address, Position location, Integer distanceMeters,
            Map<String, List<OpeningPeriod>> hours) {
        return new Restaurant(name, location, distanceMeters, "asian", List.of("vegetarian"), List.of(), null,
                hours, null, null, address, null, null, null, null, null, null, null);
    }

    /** A restaurant with address, location 500 m away, open every day all day. */
    public static Restaurant open(String name) {
        return of(name, "1 rue " + name, CENTER, 500, everyDay("00:00", "23:59"));
    }
}
